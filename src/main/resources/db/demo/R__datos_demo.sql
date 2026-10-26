-- Datos de DEMOSTRACIÓN, 100% ficticios (caso DSY1105 §5.1). NO se cargan por defecto.
--
-- Solo se ejecutan con SPRING_PROFILES_ACTIVE=demo, que añade classpath:db/demo a spring.flyway.locations
-- (application-demo.yml). application.yml y application-prod.yml no incluyen esta carpeta, así que un
-- arranque normal o de producción jamás los inserta.
--
-- Es una migración repetible (R__) e idempotente: cada INSERT comprueba que la fila no exista, de modo
-- que arrancar varias veces no duplica nada. Todo lleva el prefijo DEMO para poder reconocerlo y borrarlo.
--
-- Las coordenadas son inventadas: no corresponden a ningún recinto real.

insert into proyectos (titulo, descripcion, area_tematica, unidad_vecinal, fecha_inicio, fecha_termino, estado, actualizado_en)
select 'DEMO Alfabetización digital', 'Proyecto ficticio para probar la sincronización', 'tecnologia',
       'UV-DEMO', date '2026-08-01', date '2026-12-15', 'EN_CURSO', current_timestamp
where not exists (select 1 from proyectos where titulo = 'DEMO Alfabetización digital');

insert into eventos (proyecto_id, nombre, fecha_hora_programada, cupos_maximos, recinto, latitud, longitud, radio_metros, actualizado_en)
select p.id, 'DEMO Taller de correo electrónico', timestamp with time zone '2026-11-05 15:00:00+00', 30,
       'Sede ficticia 1', -33.5121, -70.6003, 200, current_timestamp
from proyectos p
where p.titulo = 'DEMO Alfabetización digital'
  and not exists (select 1 from eventos e where e.nombre = 'DEMO Taller de correo electrónico');

insert into eventos (proyecto_id, nombre, fecha_hora_programada, cupos_maximos, recinto, latitud, longitud, radio_metros, actualizado_en)
select p.id, 'DEMO Taller de seguridad en línea', timestamp with time zone '2026-11-12 15:00:00+00', 25,
       'Sede ficticia 2', -33.5230, -70.6100, 150, current_timestamp
from proyectos p
where p.titulo = 'DEMO Alfabetización digital'
  and not exists (select 1 from eventos e where e.nombre = 'DEMO Taller de seguridad en línea');

insert into asistentes (codigo, tipo, estado_inscripcion, actualizado_en)
select 'DEMO-001', 'VECINO', 'INSCRITO', current_timestamp
where not exists (select 1 from asistentes where codigo = 'DEMO-001');

insert into asistentes (codigo, tipo, estado_inscripcion, actualizado_en)
select 'DEMO-002', 'VECINO', 'INSCRITO', current_timestamp
where not exists (select 1 from asistentes where codigo = 'DEMO-002');

insert into asistentes (codigo, tipo, estado_inscripcion, actualizado_en)
select 'DEMO-003', 'ESTUDIANTE', 'INSCRITO', current_timestamp
where not exists (select 1 from asistentes where codigo = 'DEMO-003');

insert into asistentes (codigo, tipo, estado_inscripcion, actualizado_en)
select 'DEMO-004', 'DOCENTE', 'INSCRITO', current_timestamp
where not exists (select 1 from asistentes where codigo = 'DEMO-004');

insert into asistentes (codigo, tipo, estado_inscripcion, actualizado_en)
select 'DEMO-005', 'VECINO', 'SIN_INSCRIPCION', current_timestamp
where not exists (select 1 from asistentes where codigo = 'DEMO-005');
