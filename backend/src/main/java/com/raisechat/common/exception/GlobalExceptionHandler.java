package com.raisechat.common.exception;

import com.raisechat.channel.exception.ChannelConflictException;
import com.raisechat.channel.exception.ChannelForbiddenException;
import com.raisechat.channel.exception.ChannelNotFoundException;
import com.raisechat.user.exception.UserNotFoundException;
import com.raisechat.workspace.exception.WorkspaceForbiddenException;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

// auth パッケージの AuthExceptionHandler は Map 形式の旧仕様（後続 Issue で全体移行）。
// 本ハンドラは新規実装（user / workspace）のみに ProblemDetail を適用するため basePackages でホワイトリスト化。
// auth は basePackages に含めず、AuthExceptionHandler が引き続き処理する。
@RestControllerAdvice(basePackages = {"com.raisechat.user", "com.raisechat.workspace", "com.raisechat.channel"})
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
}
