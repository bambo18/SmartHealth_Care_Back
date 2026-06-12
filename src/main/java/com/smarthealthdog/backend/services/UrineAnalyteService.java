package com.smarthealthdog.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smarthealthdog.backend.domain.UrineAnalyte;
import com.smarthealthdog.backend.repositories.UrineAnalyteRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UrineAnalyteService {
    private final UrineAnalyteRepository urineAnalyteRepository;

    public List<UrineAnalyte> getAllUrineAnalytes() {
        return urineAnalyteRepository.findAll();
    }
}
