package com.smarthealthdog.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shelters")
public class Shelter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255, unique = true)
    private String name;

    @Column(name = "phone_number", length = 20, unique = true)
    private String phoneNumber;

    @Column(name = "address", length = 512)
    private String address;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "province", length = 50)
    private String province;

    @Column(name = "district", length = 50)
    private String district;

    @Column(name = "facility_type", length = 50)
    private String facilityType;

    @Column(name = "jurisdiction", length = 255)
    private String jurisdiction;

    @Column(name = "is_outside_district", nullable = false)
    private boolean outsideDistrict;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "source_checked_at")
    private LocalDate sourceCheckedAt;
}