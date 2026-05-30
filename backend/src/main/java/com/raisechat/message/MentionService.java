package com.raisechat.message;

import com.raisechat.channel.ChannelMemberRepository;
import com.raisechat.dm.DmRoom;
import com.raisechat.dm.DmRoomRepository;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F-12 メンションの中核。メッセージ本文から {@code @<ユーザーID>} を抽出し、
 * mentions テーブルへ永続化する。F-14 メンションバッジの前提となる。
 *
 * <p>解決先はメッセージが属するチャンネルメンバー / DM 参加者に限定し、
 * 非メンバー・投稿者自身・存在しないハンドルは無視する（アクセス権のない相手に通知が漏れない）。
 */
@Service
public class MentionService {

    // 直前が userId 構成文字（英数 / _ / -）なら @ を無視（メールアドレス等の誤検出を避ける）。
    // 続く 3〜32 文字は users.user_id の CHECK 制約（^[A-Za-z0-9_-]{3,32}$）に合わせる。
    private static final Pattern MENTION = Pattern.compile("(?<![A-Za-z0-9_-])@([A-Za-z0-9_-]{3,32})");

    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final DmRoomRepository dmRoomRepository;

    public MentionService(
            MentionRepository mentionRepository,
            UserRepository userRepository,
            ChannelMemberRepository channelMemberRepository,
            DmRoomRepository dmRoomRepository
    ) {
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.dmRoomRepository = dmRoomRepository;
    }

    /**
     * メッセージのメンションを本文と一致するよう同期する。
     * 既存メンションを一掃してから本文を再パースするため、新規送信・編集の両方で使える。
     *
     * @return 実際に保存したメンション先 userId（重複なし、本文の出現順）
     */
    @Transactional
    public List<Long> syncMentions(Message message) {
        // 編集対応: 古いメンションをクリア（新規送信時は対象 0 件で no-op）。
        mentionRepository.deleteByMessageId(message.getId());

        Set<String> handles = extractHandles(message.getBody());
        if (handles.isEmpty()) {
            return List.of();
        }

        Set<Long> allowed = allowedRecipients(message);
        Long authorId = message.getAuthor().getId();

        Map<String, User> usersByHandle = new HashMap<>();
        for (User u : userRepository.findByUserIdIn(handles)) {
            usersByHandle.put(u.getUserId(), u);
        }

        List<Long> saved = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        // handles は本文の出現順を保つ LinkedHashSet なので、戻り値も出現順になる。
        for (String handle : handles) {
            User user = usersByHandle.get(handle);
            if (user == null) {
                continue; // 存在しないハンドル
            }
            Long uid = user.getId();
            if (uid.equals(authorId) || !allowed.contains(uid) || !seen.add(uid)) {
                continue; // 自分宛て / 非メンバー / 重複
            }
            Mention mention = new Mention();
            mention.setMessage(message);
            mention.setMentionedUser(user);
            mentionRepository.save(mention);
            saved.add(uid);
        }
        mentionRepository.flush();
        return saved;
    }

    /** リスト取得用: 複数メッセージのメンション先を 1 クエリで取得し messageId ごとに束ねる（N+1 回避）。 */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> findMentionsByMessageIds(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> result = new HashMap<>();
        for (MentionRepository.MentionUserView row : mentionRepository.findByMessageIdIn(messageIds)) {
            result.computeIfAbsent(row.getMessageId(), k -> new ArrayList<>()).add(row.getUserId());
        }
        return result;
    }

    /** 本文から重複を除いたハンドル集合を出現順で抽出する。 */
    private Set<String> extractHandles(String body) {
        if (body == null || body.isEmpty()) {
            return Set.of();
        }
        Set<String> handles = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(body);
        while (matcher.find()) {
            handles.add(matcher.group(1));
        }
        return handles;
    }

    /** メッセージを閲覧できる（= メンション可能な）ユーザー ID 集合。 */
    private Set<Long> allowedRecipients(Message message) {
        if (message.getChannel() != null) {
            return new HashSet<>(
                    channelMemberRepository.findActiveUserIdsByChannelId(message.getChannel().getId()));
        }
        if (message.getDmRoom() != null) {
            DmRoom room = dmRoomRepository.findActiveByIdWithUsers(message.getDmRoom().getId()).orElse(null);
            if (room != null) {
                return new HashSet<>(List.of(room.getUserA().getId(), room.getUserB().getId()));
            }
        }
        return Set.of();
    }
}
