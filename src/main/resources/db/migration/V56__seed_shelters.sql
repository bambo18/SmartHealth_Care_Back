-- V55__seed_shelters.sql
--
-- 기준:
-- 1) Word '동물보호_입양시설_지역별_목록(1).docx'의 서울·경기·인천·부산·대구 대상 지역을 기준으로 선별
-- 2) '전국동물보호센터_최종본.xlsx'와 'animal_shelters.csv'로 시설명/주소/연락처/관할/좌표를 교차 확인
-- 3) 동일 물리 시설이 여러 관할에 반복된 경우 1행으로 통합하고 jurisdiction에 관할을 묶음
-- 4) Word의 33개 행 -> 물리 시설 중복 통합 후 30개 -> 강현림종합동물병원은 XLSX/CSV에서 교차 확인되지 않아 제외 -> 최종 29개
-- 5) 서울동물복지지원센터 마포는 전국 지정 보호센터 자료에는 없지만 Word에서 '서울시 직영 입양센터'로 명시되어 유지
--
-- 배포 전제: V54__extend_shelters_for_region_search.sql 적용 완료

CREATE TEMP TABLE seed_shelters (
    name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    address VARCHAR(512),
    latitude NUMERIC(12, 8),
    longitude NUMERIC(12, 8),
    province VARCHAR(50),
    district VARCHAR(50),
    facility_type VARCHAR(50),
    jurisdiction VARCHAR(255),
    is_outside_district BOOLEAN NOT NULL,
    is_active BOOLEAN NOT NULL,
    source VARCHAR(255),
    source_checked_at DATE
) ON COMMIT DROP;

INSERT INTO seed_shelters (
    name, phone_number, address, latitude, longitude,
    province, district, facility_type, jurisdiction,
    is_outside_district, is_active, source, source_checked_at
)
VALUES
('한국동물구조관리협회', '031-867-9119', '경기도 양주시 남면 감악산로 63-37 (남면) 한국동물구조관리협회', 37.870100, 126.983540, '경기도', '양주시', '공식 위탁 보호센터', '경기도: 구리시, 김포시, 동두천시, 양주시, 연천군, 의정부시, 파주시, 포천시, 하남시 | 서울특별시: 강남구, 강동구, 강서구, 광진구, 구로구, 금천구, 도봉구, 동대문구, 서대문구, 서초구, 성동구, 성북구, 송파구, 양천구, 영등포구, 은평구, 종로구, 중구, 중랑구', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('서초동물사랑센터', '02-6956-7980', '서울특별시 서초구 양재천로19길 22 (양재동, 숨빌딩) 서초동물사랑센터', 37.476370, 127.038155, '서울특별시', '서초구', '구립 입양·보호센터', '서울특별시: 서초구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('구로댕냥이네', '02-2636-7649', '서울특별시 구로구 경인로 472 (구로동) 구로댕냥이네', 37.505543, 126.885620, '서울특별시', '구로구', '시립 동물복지·입양센터', '서울특별시: 구로구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('서울동물복지지원센터 마포', '02-2124-2839', '서울특별시 마포구 매봉산로 31 S-PLEXCENTER 지하1층', 37.575719, 126.889995, '서울특별시', '마포구', '서울시 직영 입양센터', '서울특별시: 마포구', FALSE, TRUE, 'Word: 동물보호_입양시설_지역별_목록(서울시 직영)', DATE '2026-10-12'),
('홍익동물병원', '02-325-2026', '서울특별시 마포구 독막로 45 (합정동)', 37.548100, 126.918390, '서울특별시', '마포구', '지정 동물보호센터', '서울특별시: 마포구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('펫앤쉘터동물병원', '031-714-8392', '경기도 성남시 분당구 불정로 266 (수내동, 유신제일조합)', 37.367100, 127.127690, '경기도', '성남시', '지정 동물보호센터', '경기도: 성남시', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('용인시 동물보호센터', '031-6193-3463', '경기도 용인시 처인구 중부대로 1074-1 (삼가동) 용인시 동물보호센터', 37.243313, 127.159340, '경기도', '용인시', '시립 동물보호센터', '경기도: 용인시', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('수원시 동물보호센터', '031-5191-3557', '경기도 수원시 영통구 광교호수로 234 (하동, 수원시 동물보호센터) 하동 40-10', 37.285023, 127.078710, '경기도', '수원시', '시립 동물보호센터', '경기도: 수원시', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('고양시동물보호센터', '031-962-3232', '경기도 고양시 덕양구 고양대로 1695 (원흥동, 고양시 농업기술센터) 고양시동물보호센터', 37.649643, 126.870270, '경기도', '고양시', '시립 동물보호센터', '경기도: 고양시', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('안성시동물보호센터', '031-673-5493', '경기도 안성시 보개원삼로 157 (가사동) 미래동물병원', 37.006670, 127.274864, '경기도', '안성시', '지정 동물보호센터', '경기도: 안성시', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('경동동물병원', '032-765-9988', '인천광역시 제물포구 개항로 61 (경동) 경동동물병원', 37.471960, 126.629944, '인천광역시', '제물포구', '공식 위탁 보호센터', '인천광역시: 미추홀구, 연수구, 옹진군, 제물포구', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('삼산종합동물병원', '032-330-0075', '인천광역시 부평구 체육관로 40 (삼산동)', 37.508240, 126.736170, '인천광역시', '부평구', '지정 동물보호센터', '인천광역시: 부평구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('늘푸른동물병원', '032-507-0075', '인천광역시 부평구 후정동로 60 (삼산동)', 37.522710, 126.738390, '인천광역시', '부평구', '지정 동물보호센터', '인천광역시: 부평구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('조재진동물병원', '032-518-3800', '인천광역시 부평구 원적로 419 (산곡동)', 37.504032, 126.711770, '인천광역시', '부평구', '지정 동물보호센터', '인천광역시: 부평구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('시민동물병원', '032-426-4545', '인천광역시 부평구 경인로 855 (부평동)', 37.484318, 126.715485, '인천광역시', '부평구', '지정 동물보호센터', '인천광역시: 남동구', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('해운대구 입양센터', '051-749-5680', '부산광역시 해운대구 해운대로 1175 (송정동) 해운대구 유기동물 입양센터', 35.193250, 129.205280, '부산광역시', '해운대구', '구립 입양센터', '부산광역시: 해운대구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('(사)동부동물보호협회', '051-701-7599', '부산광역시 해운대구 송정2로13번길 46 (송정동)', 35.194817, 129.206310, '부산광역시', '해운대구', '부산시 지정 보호센터', '부산광역시: 금정구, 기장군, 남구, 동래구, 수영구', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('(사)동물보호관리협회', '051-971-6208', '부산광역시 강서구 가락대로1283번길 25-2 (봉림동)', 35.178925, 128.899750, '부산광역시', '강서구', '공식 위탁 보호센터', '부산광역시: 부산진구 | 경상남도: 김해시', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('펫동물병원', '053-474-9820', '대구광역시 수성구 달구벌대로 3144 (노변동) 펫동물병원', 35.841440, 128.700850, '대구광역시', '수성구', '지정 동물보호센터', '대구광역시: 수성구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('허동물병원', '053-752-7535', '대구광역시 수성구 수성동2가 명덕로 421 (수성동2가) 허동물병원', 35.854633, 128.616320, '대구광역시', '수성구', '지정 동물보호센터', '대구광역시: 수성구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('금강동물병원', '053-746-7111', '대구광역시 수성구 동원로 150 (만촌동) 금강동물병원', 35.871670, 128.635850, '대구광역시', '수성구', '지정 동물보호센터', '대구광역시: 수성구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('멘토동물병원', '010-7291-7579', '대구광역시 수성구 용학로 294 (범물동) 2층', 35.821556, 128.640580, '대구광역시', '수성구', '지정 동물보호센터', '대구광역시: 수성구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('세인트동물병원', '053-744-8285', '대구광역시 수성구 청호로 484 (만촌동)', 35.857838, 128.645050, '대구광역시', '수성구', '지정 동물보호센터', '대구광역시: 수성구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('신세계동물병원', '053-751-6693', '대구광역시 수성구 수성동1가 명덕로 393 (수성동1가)', 35.854717, 128.613200, '대구광역시', '수성구', '지정 동물보호센터', '대구광역시: 수성구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('제니스동물병원', '053-756-8293', '대구광역시 수성구 달구벌대로 2475 (범어동)', 35.859123, 128.633400, '대구광역시', '수성구', '지정 동물보호센터', '대구광역시: 수성구', FALSE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('대구유기동물보호센터', '053-964-6258', '대구광역시 동구 금강로 151-13 (금강동)', 35.863876, 128.735960, '대구광역시', '동구', '공식 관할 보호센터', '대구광역시: 남구, 달서구, 동구, 북구', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('119동물병원', '053-585-1195', '대구광역시 달서구 달구벌대로 1005 (호산동) 119 동물병원', 35.857166, 128.466540, '대구광역시', '달서구', '지정 동물보호센터', '대구광역시: 달성군', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('동행동물병원', '053-636-8720', '대구광역시 달서구 진천로 117 (대천동) 117,118호', 35.818893, 128.521240, '대구광역시', '달서구', '지정 동물보호센터', '대구광역시: 달성군', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12'),
('이서동물병원', '053-719-0205', '대구광역시 달서구 구마로 243 (성당동) 이서동물병원', 35.837650, 128.554370, '대구광역시', '달서구', '지정 동물보호센터', '대구광역시: 달성군', TRUE, TRUE, 'Word+전국동물보호센터_최종본.xlsx+animal_shelters.csv', DATE '2026-10-12');

-- Seed 자체 검증
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM seed_shelters) <> 29 THEN
        RAISE EXCEPTION 'V55 shelter seed count mismatch: expected 29';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM seed_shelters
        GROUP BY regexp_replace(phone_number, '\D', '', 'g')
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V55 shelter seed contains duplicate phone numbers';
    END IF;
END
$$;

-- 기존 레거시 보호소는 우선 비활성화하고, 검증된 seed만 다시 활성화한다.
-- 현재 서버 DB에 남아 있는 지역정보 없는 기존 테스트/레거시 행이 전체 조회에 섞이는 것을 방지한다.
UPDATE shelters
SET is_active = FALSE;

-- 동일 시설(전화번호 기준)이 이미 있으면 새 행을 만들지 않고 최신 검증정보로 갱신
UPDATE shelters s
SET
    name = v.name,
    phone_number = v.phone_number,
    address = v.address,
    latitude = v.latitude,
    longitude = v.longitude,
    province = v.province,
    district = v.district,
    facility_type = v.facility_type,
    jurisdiction = v.jurisdiction,
    is_outside_district = v.is_outside_district,
    is_active = TRUE,
    source = v.source,
    source_checked_at = v.source_checked_at
FROM seed_shelters v
WHERE regexp_replace(COALESCE(s.phone_number, ''), '\D', '', 'g')
      = regexp_replace(v.phone_number, '\D', '', 'g');

-- 기존에 없는 검증 시설만 신규 등록
INSERT INTO shelters (
    name,
    phone_number,
    address,
    latitude,
    longitude,
    province,
    district,
    facility_type,
    jurisdiction,
    is_outside_district,
    is_active,
    source,
    source_checked_at
)
SELECT
    v.name,
    v.phone_number,
    v.address,
    v.latitude,
    v.longitude,
    v.province,
    v.district,
    v.facility_type,
    v.jurisdiction,
    v.is_outside_district,
    TRUE,
    v.source,
    v.source_checked_at
FROM seed_shelters v
WHERE NOT EXISTS (
    SELECT 1
    FROM shelters s
    WHERE regexp_replace(COALESCE(s.phone_number, ''), '\D', '', 'g')
          = regexp_replace(v.phone_number, '\D', '', 'g')
);

-- 적용 결과 검증: seed의 29개 전화번호가 모두 활성 보호소로 존재해야 한다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_shelters v
        WHERE NOT EXISTS (
            SELECT 1
            FROM shelters s
            WHERE s.is_active = TRUE
              AND regexp_replace(COALESCE(s.phone_number, ''), '\D', '', 'g')
                  = regexp_replace(v.phone_number, '\D', '', 'g')
        )
    ) THEN
        RAISE EXCEPTION 'V55 shelter seed verification failed';
    END IF;
END
$$;
