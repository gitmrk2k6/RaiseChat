package com.raisechat.message.exception;

// F-13 検索クエリ（q）が不正（空・空白のみ）な場合に投げる。GlobalExceptionHandler で 422 に変換する。
public class SearchValidationException extends RuntimeException {
    public SearchValidationException(String message) {
        super(message);
    }
}
