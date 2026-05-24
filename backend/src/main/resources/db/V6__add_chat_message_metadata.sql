ALTER TABLE chat_messages
ADD COLUMN metadata JSON NULL AFTER content;
