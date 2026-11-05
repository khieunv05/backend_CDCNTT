-- The user names each paragraph ("Luyện viết IELTS", "Thư xin việc"...) so the notebook is
-- readable without opening the full text. Only `text` is sent to the AI grader; `title` is
-- stored as-is for display, so it stays OUT of the grading payload and the prompt.
-- Existing rows (pre-title data) get an empty label; the app requires a title on every new row.
ALTER TABLE phrases
    ADD COLUMN title VARCHAR(255) NOT NULL DEFAULT '' AFTER user_id;
