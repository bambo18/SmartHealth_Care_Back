
package com.smarthealthdog.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "health_check_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthCheckAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private HealthCheckArea area;

    @Column(name = "question_index", nullable = false)
    private Integer questionIndex;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_weight", nullable = false, length = 10)
    private String questionWeight;

    @Column(name = "answer_value", nullable = false)
    private Short answerValue;

    @Builder
    private HealthCheckAnswer(
            HealthCheckArea area,
            Integer questionIndex,
            String questionText,
            String questionWeight,
            Short answerValue
    ) {
        this.area = area;
        this.questionIndex = questionIndex;
        this.questionText = questionText;
        this.questionWeight = questionWeight;
        this.answerValue = answerValue;
    }
}
