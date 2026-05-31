package com.raisechat.common.exception;

import com.raisechat.channel.exception.ChannelConflictException;
import com.raisechat.channel.exception.ChannelForbiddenException;
import com.raisechat.channel.exception.ChannelNotFoundException;
import com.raisechat.dm.exception.DmForbiddenException;
import com.raisechat.dm.exception.DmNotFoundException;
import com.raisechat.dm.exception.DmValidationException;
import com.raisechat.message.exception.AttachmentTooLargeException;
import com.raisechat.message.exception.AttachmentValidationException;
import com.raisechat.message.exception.MessageForbiddenException;
import com.raisechat.message.exception.MessageNotFoundException;
import com.raisechat.message.exception.SearchValidationException;
import com.raisechat.message.exception.UnsupportedAttachmentTypeException;
import com.raisechat.user.exception.AvatarTooLargeException;
import com.raisechat.user.exception.AvatarValidationException;
import com.raisechat.user.exception.UnsupportedAvatarTypeException;
import com.raisechat.user.exception.UserNotFoundException;
import com.raisechat.workspace.exception.InviteGoneException;
import com.raisechat.workspace.exception.InviteNotFoundException;
import com.raisechat.workspace.exception.WorkspaceConflictException;
import com.raisechat.workspace.exception.WorkspaceForbiddenException;
import com.raisechat.workspace.exception.WorkspaceMemberNotFoundException;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.List;
import java.util.Map;

// auth パッケージの AuthExceptionHandler は Map 形式の旧仕様（後続 Issue で全体移行）。
// 本ハンドラは新規実装（user / workspace）のみに ProblemDetail を適用するため basePackages でホワイトリスト化。
// auth は basePackages に含めず、AuthExceptionHandler が引き続き処理する。
@RestControllerAdvice(basePackages = {"com.raisechat.user", "com.raisechat.workspace", "com.raisechat.channel", "com.raisechat.message", "com.raisechat.dm", "com.raisechat.notification"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://raisechat.example.com/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()
                ))
                .toList();

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(PROBLEM_BASE + "validation"));
        pd.setTitle("Validation Failed");
        pd.setDetail("リクエストボディに不正な値があります");
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEM_BASE + "not-found"));
        pd.setTitle("Resource Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // アバター画像が対応外の MIME タイプ → 415。
    @ExceptionHandler(UnsupportedAvatarTypeException.class)
    public ProblemDetail handleUnsupportedAvatarType(UnsupportedAvatarTypeException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        pd.setType(URI.create(PROBLEM_BASE + "unsupported-media-type"));
        pd.setTitle("Unsupported Media Type");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // アバター画像がサイズ上限超過 → 413。
    @ExceptionHandler(AvatarTooLargeException.class)
    public ProblemDetail handleAvatarTooLarge(AvatarTooLargeException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setType(URI.create(PROBLEM_BASE + "payload-too-large"));
        pd.setTitle("Payload Too Large");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // multipart 解析段階でサイズ上限を超えた場合の保険（アバター上限超過も 413 に揃える）。
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setType(URI.create(PROBLEM_BASE + "payload-too-large"));
        pd.setTitle("Payload Too Large");
        pd.setDetail("アップロードサイズが上限を超えています");
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(AvatarValidationException.class)
    public ProblemDetail handleAvatarValidation(AvatarValidationException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(PROBLEM_BASE + "validation"));
        pd.setTitle("Validation Failed");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ProblemDetail handleWorkspaceNotFound(WorkspaceNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEM_BASE + "not-found"));
        pd.setTitle("Resource Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(WorkspaceForbiddenException.class)
    public ProblemDetail handleWorkspaceForbidden(WorkspaceForbiddenException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setType(URI.create(PROBLEM_BASE + "forbidden"));
        pd.setTitle("Forbidden");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ProblemDetail handleWorkspaceMemberNotFound(WorkspaceMemberNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEM_BASE + "not-found"));
        pd.setTitle("Resource Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(WorkspaceConflictException.class)
    public ProblemDetail handleWorkspaceConflict(WorkspaceConflictException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setType(URI.create(PROBLEM_BASE + "conflict"));
        pd.setTitle("Conflict");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(ChannelNotFoundException.class)
    public ProblemDetail handleChannelNotFound(ChannelNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEM_BASE + "not-found"));
        pd.setTitle("Resource Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(ChannelForbiddenException.class)
    public ProblemDetail handleChannelForbidden(ChannelForbiddenException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setType(URI.create(PROBLEM_BASE + "forbidden"));
        pd.setTitle("Forbidden");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(ChannelConflictException.class)
    public ProblemDetail handleChannelConflict(ChannelConflictException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setType(URI.create(PROBLEM_BASE + "conflict"));
        pd.setTitle("Conflict");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(MessageNotFoundException.class)
    public ProblemDetail handleMessageNotFound(MessageNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEM_BASE + "not-found"));
        pd.setTitle("Resource Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(MessageForbiddenException.class)
    public ProblemDetail handleMessageForbidden(MessageForbiddenException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setType(URI.create(PROBLEM_BASE + "forbidden"));
        pd.setTitle("Forbidden");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // 添付ファイルが対応外の MIME タイプ → 415（アバターと同様）。
    @ExceptionHandler(UnsupportedAttachmentTypeException.class)
    public ProblemDetail handleUnsupportedAttachmentType(UnsupportedAttachmentTypeException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        pd.setType(URI.create(PROBLEM_BASE + "unsupported-media-type"));
        pd.setTitle("Unsupported Media Type");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // 添付ファイルがサイズ上限（10MB）超過 → 413。
    @ExceptionHandler(AttachmentTooLargeException.class)
    public ProblemDetail handleAttachmentTooLarge(AttachmentTooLargeException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setType(URI.create(PROBLEM_BASE + "payload-too-large"));
        pd.setTitle("Payload Too Large");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(AttachmentValidationException.class)
    public ProblemDetail handleAttachmentValidation(AttachmentValidationException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(PROBLEM_BASE + "validation"));
        pd.setTitle("Validation Failed");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // F-13 検索クエリ（q）が空・空白のみ → 422。
    @ExceptionHandler(SearchValidationException.class)
    public ProblemDetail handleSearchValidation(SearchValidationException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(PROBLEM_BASE + "validation"));
        pd.setTitle("Validation Failed");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(DmNotFoundException.class)
    public ProblemDetail handleDmNotFound(DmNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEM_BASE + "not-found"));
        pd.setTitle("Resource Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(DmForbiddenException.class)
    public ProblemDetail handleDmForbidden(DmForbiddenException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setType(URI.create(PROBLEM_BASE + "forbidden"));
        pd.setTitle("Forbidden");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(DmValidationException.class)
    public ProblemDetail handleDmValidation(DmValidationException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(PROBLEM_BASE + "validation"));
        pd.setTitle("Validation Failed");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(InviteNotFoundException.class)
    public ProblemDetail handleInviteNotFound(InviteNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEM_BASE + "not-found"));
        pd.setTitle("Resource Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // 招待は存在するが利用不可（期限切れ / 無効化済み / 使用上限）→ 410 Gone。
    @ExceptionHandler(InviteGoneException.class)
    public ProblemDetail handleInviteGone(InviteGoneException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.GONE);
        pd.setType(URI.create(PROBLEM_BASE + "gone"));
        pd.setTitle("Gone");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }
}
