package com.example.parcial.parcial2.repositories;

import com.example.parcial.parcial2.domain.entities.Book;
import com.example.parcial.parcial2.domain.entities.Lector;
import com.example.parcial.parcial2.domain.entities.Movement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovementRepository extends JpaRepository<Movement, UUID> {

    Optional<Movement> findTopByLectorAndBookOrderByTimestampDesc(Lector lector, Book book);
}
