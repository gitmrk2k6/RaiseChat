package com.raisechat.user;

import com.raisechat.storage.ObjectStorage;
import com.raisechat.user.dto.UpdateMeRequest;
import com.raisechat.user.dto.UserResponse;
import com.raisechat.user.exception.AvatarTooLargeException;
import com.raisechat.user.exception.AvatarValidationException;
import com.raisechat.user.exception.UnsupportedAvatarTypeException;
import com.raisechat.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    // アバター画像の上限サイズ（2MB）と許可 MIME → 拡張子の対応。
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif"
    );

    private final UserRepository userRepository;
    private final ObjectStorage objectStorage;

    public UserService(UserRepository userRepository, ObjectStorage objectStorage) {
        this.userRepository = userRepository;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateMeRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (req.displayName() != null) {
            user.setDisplayName(req.displayName());
        }
        if (req.statusMessage() != null) {
            user.setStatusMessage(req.statusMessage());
        }

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AvatarValidationException("アバター画像が指定されていません");
        }

        String contentType = file.getContentType();
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new UnsupportedAvatarTypeException(contentType);
        }

        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new AvatarTooLargeException(MAX_AVATAR_BYTES);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 同一ユーザーでも毎回キーを変え、ブラウザ/CDN のキャッシュで古い画像が残らないようにする。
        String key = "avatars/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("アバター画像の読み込みに失敗しました", e);
        }

        String avatarUrl = objectStorage.upload(key, data, contentType);
        user.setAvatarUrl(avatarUrl);

        return UserResponse.from(user);
    }
}
