-- V26__cleanup_condition_translations_and_repopulate.sql

-- 1. DELETE all existing condition translations
DELETE FROM condition_translations;

-- 2. INSERT new condition translations using a CTE for dynamic ID lookup
WITH condition_translations_data AS (
    -- DOG Conditions
    SELECT 'Blepharitis' AS name, 'DOG' AS species, '안검염' AS translated_name, '눈꺼풀의 염증으로, 눈꺼풀이 붓거나 빨갛게 되고 가렵거나 비듬 등이 생길 수 있습니다.' AS translated_description, 1 AS language_id
    UNION ALL SELECT 'Cataract', 'DOG', '백내장', '눈의 수정체가 혼탁해져 빛이 망막에 제대로 도달하지 못하게 되어 시력이 저하되는 질환입니다.', 1
    UNION ALL SELECT 'Conjunctivitis', 'DOG', '결막염', '눈꺼풀 안쪽과 안구 앞부분을 덮는 결막에 염증이 생긴 것입니다. 눈이 충혈되고 눈곱이 끼는 증상을 보입니다.', 1
    UNION ALL SELECT 'Entropion', 'DOG', '안검내반', '눈꺼풀이 안구 쪽으로 말려 들어가 눈꺼풀의 털이나 피부가 각막을 자극하는 질환입니다.', 1
    UNION ALL SELECT 'Eyelid Tumor', 'DOG', '안검 종양', '눈꺼풀에 발생하는 비정상적인 혹 또는 덩어리입니다.', 1
    UNION ALL SELECT 'Incontinence', 'DOG', '요실금', '소변을 조절하는 능력에 문제가 생겨 의지와 상관없이 소변을 흘리는 증상입니다.', 1
    UNION ALL SELECT 'Non-ulcerative Keratitis', 'DOG', '비궤양성 각막염', '각막에 염증이 있지만, 각막 표면층이 손상되어 파이지 않은 상태의 각막 질환입니다.', 1
    UNION ALL SELECT 'Nuclear Sclerosis', 'DOG', '핵경화증', '나이가 들면서 수정체의 중심부가 단단해지고 밀도가 높아져 뿌옇게 보이는 자연스러운 노화 현상입니다.', 1
    UNION ALL SELECT 'Pigmentary Keratitis', 'DOG', '색소성 각막염', '각막에 검은색 또는 갈색의 멜라닌 색소가 침착되는 만성 염증성 질환입니다.', 1
    UNION ALL SELECT 'Ulcerative Keratitis', 'DOG', '궤양성 각막염', '각막 표면층이 손상되어 파인 상태, 즉 각막 궤양이 발생한 각막 염증입니다.', 1

    -- CAT Conditions
    UNION ALL SELECT 'Blepharitis', 'CAT', '안검염', '눈꺼풀에 발생하는 염증으로, 눈꺼풀이 붓고 빨갛게 되거나 가려움증, 비듬 등을 유발할 수 있습니다.', 1
    UNION ALL SELECT 'Conjunctivitis', 'CAT', '결막염', '눈꺼풀 안쪽과 안구를 덮는 얇은 막인 결막에 염증이 생긴 것입니다. 눈 충혈, 눈곱, 눈물 흘림 등이 주요 증상입니다.', 1
    UNION ALL SELECT 'Corneal Dystrophy', 'CAT', '각막 이영양증', '각막(검은 눈동자)에 유전적인 문제로 인해 비염증성 혼탁이 발생하는 질환입니다. 통증은 없으나 시력에 영향을 줄 수 있습니다.', 1
    UNION ALL SELECT 'Corneal Ulcer', 'CAT', '각막 궤양', '각막 표면층이 손상되어 궤양(상처)이 발생한 상태입니다. 눈을 깜빡이거나 비비는 등 통증이 심하며, 감염 시 위험합니다.', 1
    UNION ALL SELECT 'Non-ulcerative Keratitis', 'CAT', '비궤양성 각막염', '각막에 염증이 생겼으나, 각막 표면층(상피)이 파이지 않고 손상되지 않은 상태의 각막 질환입니다.', 1
)
INSERT INTO condition_translations (condition_id, translated_name, translated_description, language_id)
SELECT 
    c.id AS condition_id,
    ctd.translated_name,
    ctd.translated_description,
    ctd.language_id
FROM 
    condition_translations_data ctd
JOIN 
    conditions c ON ctd.name = c.name AND ctd.species = c.species;

---

-- 3. DELETE all existing check_method translations
DELETE FROM check_method_translations;

-- 4. INSERT new check_method translations using a JOIN for dynamic ID lookup
INSERT INTO check_method_translations (check_method_id, language_id, translated_description, translated_name)
SELECT
    cm.id AS check_method_id,
    data.language_id,
    data.translated_description,
    data.translated_name
FROM
    check_methods cm
JOIN (
    SELECT 'Eye Examination' AS original_name, 1 AS language_id, '안과 검사' AS translated_name, 'AI 모델을 통한 안구 사진 분석을 기반으로 다양한 안과 질환을 진단하는 검사 방법입니다. 이 검사는 개와 고양이의 눈 건강 상태를 평가하고, 백내장, 결막염, 각막염 등 여러 안과 질환을 조기에 발견하는 데 도움을 줍니다.' AS translated_description
    UNION ALL
    SELECT 'Urine Analysis', 1, '소변 검사', 'AI 모델을 통한 소변 검사 스트립 분석을 기반으로 다양한 비뇨기 질환을 진단하는 검사 방법입니다. 이 검사는 개와 고양이의 소변 내 성분을 평가하여 요로 감염, 신장 질환, 당뇨병 등 여러 비뇨기 질환을 조기에 발견하는 데 도움을 줍니다.'
) AS data ON cm.name = data.original_name;