-- 1. 기존 fk_ct_language 제약 조건 삭제 (CASCADE 설정)
ALTER TABLE condition_translations
    DROP CONSTRAINT fk_ct_language;

-- 2. 새로운 fk_ct_language 제약 조건 추가 (RESTRICT 설정)
ALTER TABLE condition_translations
    ADD CONSTRAINT fk_ct_language
    FOREIGN KEY (language_id)
    REFERENCES languages (id) ON DELETE RESTRICT;

-- 1. 기존 fk_cmt_check_method 제약 조건 삭제 (CASCADE 설정)
ALTER TABLE check_method_translations
    DROP CONSTRAINT fk_cmt_check_method;

-- 2. 기존 fk_cmt_language 제약 조건 삭제 (CASCADE 설정)
ALTER TABLE check_method_translations
    DROP CONSTRAINT fk_cmt_language;

-- 3. 새로운 fk_cmt_check_method 제약 조건 추가 (RESTRICT 설정)
ALTER TABLE check_method_translations
    ADD CONSTRAINT fk_cmt_check_method
    FOREIGN KEY (check_method_id)
    REFERENCES check_methods (id) ON DELETE RESTRICT;

-- 4. 새로운 fk_cmt_language 제약 조건 추가 (RESTRICT 설정)
ALTER TABLE check_method_translations
    ADD CONSTRAINT fk_cmt_language
    FOREIGN KEY (language_id)
    REFERENCES languages (id) ON DELETE RESTRICT;