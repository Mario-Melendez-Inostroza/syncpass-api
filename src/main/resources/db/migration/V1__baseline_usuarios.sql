-- V1: tablas de autenticación que ya existían antes de usar Flyway.
--
-- Hasta ahora las creó Hibernate con ddl-auto=update. Esta migración las describe tal como Hibernate las
-- espera (ver build/schema.sql, generado por EsquemaGeneradoTest).
--
--  * En una base NUEVA se ejecuta y crea las tablas.
--  * En una base que ya tiene estas tablas (p. ej. la de Supabase) NO se ejecuta: con baseline-on-migrate y
--    baseline-version=1 Flyway da V1 por aplicada y solo corre V2 en adelante. Eso presupone que la tabla
--    real coincide con esta; hay que comprobarlo antes (docs/MIGRACIONES.md, "Paso 0").

create table usuarios (
    id      varchar(64)  not null,
    email   varchar(255) not null,
    nombre  varchar(255) not null,
    primary key (id),
    constraint uk_usuarios_email unique (email)
);

create table usuario_roles (
    usuario_id varchar(64)  not null,
    rol        varchar(255) not null,
    primary key (usuario_id, rol),
    constraint fk_usuario_roles_usuario foreign key (usuario_id) references usuarios (id)
);
