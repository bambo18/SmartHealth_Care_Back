//repository = 엔티티(pet)데이터를 db에 저장하거나 꺼내오는 창구
//sql 안 써도 curd 가능 + 필요한 쿼리는 메서드명 규칙으로 자동 생성
package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.Pet;

public interface PetRepository extends JpaRepository<Pet, Long> {
    List<Pet> findByOwnerId(Long ownerId);
    
}
