package com.smarthealthdog.backend.services;

import java.util.List;
import java.util.Map;
import com.smarthealthdog.backend.domain.PetSpecies;

/** v1: must stay aligned with frontend src/data/selfCheck.ts */
public final class HealthCheckCatalog {
    private HealthCheckCatalog() {}
    public record Question(String text, String weight) {}
    public record Area(String key, String title, List<Question> questions) {}

    public static final String VERSION = "v1";
    public static final Map<PetSpecies, List<Area>> AREAS = Map.of(
        PetSpecies.DOG, List.of(
            new Area("ear", "귀", List.of(
                new Question("귀를 평소보다 자주 긁나요?", "1"),
                new Question("머리를 평소보다 자주 흔드나요?", "1"),
                new Question("평소와 다른 귀지·분비물 또는 심한 냄새가 있나요?", "2"),
                new Question("귀 안쪽이 평소보다 붉나요?", "2"),
                new Question("귀를 만질 때 통증을 보이거나 귀가 부어 있나요?", "3"),
                new Question("걷거나 서 있을 때 균형을 잡지 못하나요?", "urgent"),
                new Question("머리가 한쪽으로 심하게 기울어져 있나요?", "urgent")
            )),
            new Area("oral", "구강·잇몸", List.of(
                new Question("입 냄새가 평소보다 심해졌나요?", "1"),
                new Question("눈에 띄는 치석이 있나요?", "1"),
                new Question("잇몸이 붉거나 부어 있나요?", "2"),
                new Question("잇몸에서 피가 나나요?", "2"),
                new Question("음식을 먹다가 떨어뜨리거나 씹는 방식이 달라졌나요?", "3"),
                new Question("평소보다 침을 많이 흘리나요?", "3"),
                new Question("입 통증 때문에 음식을 거의 먹지 못하나요?", "urgent")
            )),
            new Area("anal", "항문·배변", List.of(
                new Question("엉덩이를 바닥에 끌고 다니나요?", "1"),
                new Question("항문 주변을 평소보다 자주 핥나요?", "1"),
                new Question("변이 평소보다 무르거나 형태가 크게 달라졌나요?", "2"),
                new Question("변에 점액이 보이나요?", "2"),
                new Question("항문 주변이 붉거나 부어 있나요?", "2"),
                new Question("배변할 때 힘들어하거나 통증을 보이나요?", "3"),
                new Question("항문 주변에 고름·진물 등이 있나요?", "3"),
                new Question("많은 양의 출혈 또는 검은 변이 나타나나요?", "urgent"),
                new Question("항문이나 직장 조직이 밖으로 튀어나왔나요?", "urgent")
            )),
            new Area("intake", "섭취량", List.of(
                new Question("식사량이 평소보다 감소했나요?", "2"),
                new Question("식사량이 늘었는데 체중이 감소하고 있나요?", "2"),
                new Question("물을 평소보다 많이 마시나요?", "2"),
                new Question("물을 평소보다 적게 마시나요?", "2"),
                new Question("하루 가까이 거의 먹지 못했나요?", "3"),
                new Question("음식을 먹으려고 해도 씹거나 삼키기 어려워하나요?", "3"),
                new Question("24시간 이상 음식 또는 물을 거의 섭취하지 못했나요?", "urgent")
            )),
            new Area("activity", "활동·수면", List.of(
                new Question("평소와 비교해 수면 시간이 크게 달라졌나요?", "1"),
                new Question("밤에 자주 깨거나 안절부절못하나요?", "2"),
                new Question("산책·놀이 등 활동량이 감소했나요?", "2"),
                new Question("평소 좋아하던 활동에 관심이 줄었나요?", "2"),
                new Question("갑자기 매우 무기력하거나 거의 움직이지 않으려고 하나요?", "3"),
                new Question("쉬는 중에도 호흡이 매우 힘들어 보이나요?", "urgent")
            ))
        ),
        PetSpecies.CAT, List.of(
            new Area("ear", "귀", List.of(
                new Question("귀를 평소보다 자주 긁나요?", "1"),
                new Question("머리를 평소보다 자주 흔드나요?", "1"),
                new Question("평소와 다른 귀지·분비물 또는 심한 냄새가 있나요?", "2"),
                new Question("귀 안쪽이 평소보다 붉나요?", "2"),
                new Question("귀를 만질 때 통증을 보이거나 귀가 부어 있나요?", "3"),
                new Question("걷거나 점프할 때 균형을 잡지 못하나요?", "urgent"),
                new Question("머리가 한쪽으로 심하게 기울어져 있나요?", "urgent")
            )),
            new Area("oral", "구강·잇몸", List.of(
                new Question("입 냄새가 평소보다 심해졌나요?", "1"),
                new Question("눈에 띄는 치석이 있나요?", "1"),
                new Question("잇몸이 붉거나 부어 있나요?", "2"),
                new Question("잇몸에서 피가 나나요?", "2"),
                new Question("음식을 먹다가 떨어뜨리나요?", "3"),
                new Question("평소보다 침을 많이 흘리나요?", "3"),
                new Question("입이나 얼굴 주변을 앞발로 반복해서 만지나요?", "2"),
                new Question("입 통증 때문에 음식을 거의 먹지 못하나요?", "urgent")
            )),
            new Area("anal", "항문·배변", List.of(
                new Question("엉덩이를 바닥에 끌고 다니나요?", "1"),
                new Question("항문 주변을 평소보다 자주 핥나요?", "1"),
                new Question("변 상태가 평소와 크게 달라졌나요?", "2"),
                new Question("변에 점액이 보이나요?", "2"),
                new Question("항문 주변이 붉거나 부어 있나요?", "2"),
                new Question("배변할 때 힘들어하거나 통증을 보이나요?", "3"),
                new Question("화장실에 반복해서 들어가지만 배변하지 못하나요?", "3"),
                new Question("항문 주변에 고름·진물이 있나요?", "3"),
                new Question("많은 양의 출혈 또는 검은 변이 나타나나요?", "urgent"),
                new Question("화장실에 반복해서 가지만 소변이 거의 또는 전혀 나오지 않나요?", "urgent")
            )),
            new Area("intake", "섭취량", List.of(
                new Question("식사량이 평소보다 감소했나요?", "3"),
                new Question("좋아하던 음식에도 관심을 보이지 않나요?", "2"),
                new Question("식사량이 늘었는데 체중이 감소하나요?", "2"),
                new Question("물을 평소보다 많이 마시나요?", "2"),
                new Question("물을 평소보다 적게 마시나요?", "2"),
                new Question("음식을 먹으려고 하지만 씹거나 삼키기 어려워하나요?", "3"),
                new Question("하루 가까이 거의 먹지 못했나요?", "3"),
                new Question("24시간 이상 거의 음식을 먹지 못했나요?", "urgent")
            )),
            new Area("activity", "활동·수면", List.of(
                new Question("평소와 비교해 수면 시간이 크게 달라졌나요?", "1"),
                new Question("안절부절못하거나 자주 깨나요?", "2"),
                new Question("놀이·점프 등 활동량이 감소했나요?", "2"),
                new Question("평소보다 숨어 있는 시간이 늘었나요?", "2"),
                new Question("그루밍 횟수가 감소했나요?", "2"),
                new Question("특정 부위를 지나치게 그루밍하여 털이 빠졌나요?", "2"),
                new Question("사람이나 다른 동물과의 접촉을 평소보다 피하나요?", "2"),
                new Question("갑자기 매우 무기력하거나 거의 움직이지 않나요?", "3"),
                new Question("쉬고 있는데도 호흡이 매우 힘들어 보이나요?", "urgent")
            ))
        )
    );
}
