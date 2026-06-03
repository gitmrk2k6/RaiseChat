-- F-10 file-only 添付: 本文なし（ファイルのみ）のメッセージを許可するため、
-- 本文長の下限 1 を撤廃する。body は NOT NULL のまま空文字 ('') を許容する。
-- 「本文か添付のどちらかは必須」はアプリ層（MessageController）で担保する
-- （添付の有無は attachments テーブル＝別行のため DB の CHECK では本文と相関できない）。
ALTER TABLE messages DROP CONSTRAINT messages_body_length;
ALTER TABLE messages ADD CONSTRAINT messages_body_length CHECK (char_length(body) <= 4000);
