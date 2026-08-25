-- V23__insert_various_rows_related_to_conditions.sql

-- This should be in a Vxx migration that runs before V23
ALTER TABLE check_methods
ADD CONSTRAINT uq_check_methods_name UNIQUE (name);

----------------------------------------------------------
-- Insert Dog Conditions (Species: DOG)
----------------------------------------------------------
INSERT INTO conditions (name, species, description) VALUES
('Blepharitis', 'DOG', '눈꺼풀의 염증으로, 눈꺼풀이 붓거나 빨갛게 되고 가렵거나 비듬 등이 생길 수 있습니다.'),
('Cataract', 'DOG', '눈의 수정체가 혼탁해져 빛이 망막에 제대로 도달하지 못하게 되어 시력이 저하되는 질환입니다.'),
('Conjunctivitis', 'DOG', '눈꺼풀 안쪽과 안구 앞부분을 덮는 결막에 염증이 생긴 것입니다. 눈이 충혈되고 눈곱이 끼는 증상을 보입니다.'),
('Entropion', 'DOG', '눈꺼풀이 안구 쪽으로 말려 들어가 눈꺼풀의 털이나 피부가 각막을 자극하는 질환입니다.'),
('Eyelid Tumor', 'DOG', '눈꺼풀에 발생하는 비정상적인 혹 또는 덩어리입니다.'),
('Incontinence', 'DOG', '소변을 조절하는 능력에 문제가 생겨 의지와 상관없이 소변을 흘리는 증상입니다.'),
('Non-ulcerative Keratitis', 'DOG', '각막에 염증이 있지만, 각막 표면층이 손상되어 파이지 않은 상태의 각막 질환입니다.'),
('Nuclear Sclerosis', 'DOG', '나이가 들면서 수정체의 중심부가 단단해지고 밀도가 높아져 뿌옇게 보이는 자연스러운 노화 현상입니다.'),
('Pigmentary Keratitis', 'DOG', '각막에 검은색 또는 갈색의 멜라닌 색소가 침착되는 만성 염증성 질환입니다.'),
('Ulcerative Keratitis', 'DOG', '각막 표면층이 손상되어 파인 상태, 즉 각막 궤양이 발생한 각막 염증입니다.')
ON CONFLICT (name, species) DO NOTHING;


----------------------------------------------------------
-- Insert Cat Conditions (Species: CAT)
----------------------------------------------------------
INSERT INTO conditions (name, species, description) VALUES
('Blepharitis', 'CAT', '눈꺼풀에 발생하는 염증으로, 눈꺼풀이 붓고 빨갛게 되거나 가려움증, 비듬 등을 유발할 수 있습니다.'),
('Conjunctivitis', 'CAT', '눈꺼풀 안쪽과 안구를 덮는 얇은 막인 결막에 염증이 생긴 것입니다. 눈 충혈, 눈곱, 눈물 흘림 등이 주요 증상입니다.'),
('Corneal Dystrophy', 'CAT', '각막(검은 눈동자)에 유전적인 문제로 인해 비염증성 혼탁이 발생하는 질환입니다. 통증은 없으나 시력에 영향을 줄 수 있습니다.'),
('Corneal Ulcer', 'CAT', '각막 표면층이 손상되어 궤양(상처)이 발생한 상태입니다. 눈을 깜빡이거나 비비는 등 통증이 심하며, 감염 시 위험합니다.'),
('Non-ulcerative Keratitis', 'CAT', '각막에 염증이 생겼으나, 각막 표면층(상피)이 파이지 않고 손상되지 않은 상태의 각막 질환입니다.')
ON CONFLICT (name, species) DO NOTHING;

----------------------------------------------------------
-- 1. Insert the 'Eye Examination' record
----------------------------------------------------------
INSERT INTO check_methods (name, description)
VALUES ('Eye Examination', 'AI 모델을 통한 안구 사진 분석을 기반으로 다양한 안과 질환을 진단하는 검사 방법입니다. 이 검사는 개와 고양이의 눈 건강 상태를 평가하고, 백내장, 결막염, 각막염 등 여러 안과 질환을 조기에 발견하는 데 도움을 줍니다.'),
       ('Urine Analysis', 'AI 모델을 통한 소변 검사 스트립 분석을 기반으로 다양한 비뇨기 질환을 진단하는 검사 방법입니다. 이 검사는 개와 고양이의 소변 내 성분을 평가하여 요로 감염, 신장 질환, 당뇨병 등 여러 비뇨기 질환을 조기에 발견하는 데 도움을 줍니다.')
ON CONFLICT (name) DO NOTHING;


----------------------------------------------------------
-- 2. Insert the Many-to-Many associations
----------------------------------------------------------
-- Define the name of the check method we are linking.
WITH eye_exam_id AS (
    SELECT id
    FROM check_methods
    WHERE name = 'Eye Examination'
),

-- List all the dog diseases, including Incontinence, that are diagnosed by an eye exam.
dog_conditions_ids AS (
    SELECT id AS condition_id
    FROM conditions
    WHERE species = 'DOG'
    AND name IN (
        'Blepharitis',
        'Cataract',
        'Conjunctivitis',
        'Entropion',
        'Eyelid Tumor',
        'Incontinence', -- INCLUDED as per your instruction
        'Non-ulcerative Keratitis',
        'Nuclear Sclerosis',
        'Pigmentary Keratitis',
        'Ulcerative Keratitis'
    )
),

-- List all the cat diseases that are diagnosed by an eye exam.
cat_conditions_ids AS (
    SELECT id AS condition_id
    FROM conditions
    WHERE species = 'CAT'
    AND name IN (
        'Blepharitis',
        'Conjunctivitis',
        'Corneal Dystrophy',
        'Corneal Ulcer',
        'Non-ulcerative Keratitis'
    )
)

-- Insert the associations into the joining table.
INSERT INTO condition_check_methods (condition_id, check_method_id)
SELECT condition_id, (SELECT id FROM eye_exam_id) AS check_method_id
FROM dog_conditions_ids
UNION ALL
SELECT condition_id, (SELECT id FROM eye_exam_id) AS check_method_id
FROM cat_conditions_ids
ON CONFLICT (condition_id, check_method_id) DO NOTHING;