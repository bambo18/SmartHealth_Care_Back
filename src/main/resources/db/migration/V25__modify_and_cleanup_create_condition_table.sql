-- V25__migrate_ko_data_and_clean_core_tables.sql

ALTER TABLE check_method_translations
ADD COLUMN translated_name VARCHAR(255) NOT NULL;

----------------------------------------------------------
-- Prerequisites: Extract Language ID
----------------------------------------------------------

-- 1. Korean 언어 ID를 임시 테이블에 저장합니다.
CREATE TEMPORARY TABLE ko_lang_id AS
SELECT id FROM languages WHERE code = 'ko';

-- 오류 방지를 위해 언어 ID가 존재하는지 확인합니다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ko_lang_id) THEN
        RAISE EXCEPTION 'Korean language (ko) ID not found in the languages table.';
    END IF;
END $$;


----------------------------------------------------------
-- 2. Move Condition Data to Translation Table (KEEPING CORE COLUMN)
----------------------------------------------------------

-- ⚠️ NOTE: The 'conditions' table is kept AS IS.
-- The existing 'description' column is used as the source for the Korean translation
-- but is NOT renamed or dropped, allowing it to remain as a fallback column.

-- 한국어 번역 데이터를 condition_translations 테이블에 삽입합니다.
-- 이때, conditions.name을 translated_name으로 사용하고, conditions.description을 번역 내용으로 사용합니다.
INSERT INTO condition_translations (condition_id, language_id, translated_name, translated_description)
SELECT
    c.id,
    (SELECT id FROM ko_lang_id),
    c.name, -- 현재는 영문 이름과 동일하게 삽입 (향후 한국어 명칭으로 업데이트 가능)
    c.description -- 👈 Using the existing column without alteration
FROM conditions c
ON CONFLICT (condition_id, language_id) DO NOTHING;

-- ⚠️ No ALTER TABLE ... DROP COLUMN here, as requested.


----------------------------------------------------------
-- 3. Move Check Method Data to Translation Table (CLEANING UP CORE COLUMN)
----------------------------------------------------------

-- 한국어 번역 데이터를 check_method_translations 테이블에 삽입합니다.
INSERT INTO check_method_translations (check_method_id, language_id, translated_name, translated_description)
SELECT
    cm.id,
    (SELECT id FROM ko_lang_id),
    cm.name,
    cm.description
FROM check_methods cm
ON CONFLICT (check_method_id, language_id) DO NOTHING;

-- 임시 테이블을 정리합니다.
DROP TABLE ko_lang_id;