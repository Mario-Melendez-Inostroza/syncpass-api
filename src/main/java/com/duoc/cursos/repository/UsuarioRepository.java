package com.duoc.cursos.repository;

import com.duoc.cursos.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, String> {
    
    boolean existsByEmail(String email);
}