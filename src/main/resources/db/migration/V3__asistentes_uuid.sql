-- V3: identificador de cliente (uuid) para asistentes dados de alta en terreno desde el móvil (caso §3.1:
-- el monitor "anota a quienes llegan sin inscripción previa").
--
-- El teléfono genera el uuid y lo reenvía hasta que el servidor confirma: es la clave de idempotencia, igual
-- que en asistencias y encuestas. Es NULL para los asistentes que ya existían (catálogo cargado por el servidor).
-- Sin datos personales: el asistente sigue siendo solo un código sintético y un tipo (caso §5.1).

alter table asistentes add column uuid varchar(36);
alter table asistentes add constraint uk_asistentes_uuid unique (uuid);
