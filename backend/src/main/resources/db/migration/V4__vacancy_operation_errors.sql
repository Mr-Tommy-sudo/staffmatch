-- The legacy last_error has no reliable operation identity. Keep it as historical
-- data and leave the new operation errors empty for existing vacancies.
ALTER TABLE vacancies
    ADD COLUMN generation_error TEXT,
    ADD COLUMN matching_error TEXT,
    ADD COLUMN ranking_error TEXT;
