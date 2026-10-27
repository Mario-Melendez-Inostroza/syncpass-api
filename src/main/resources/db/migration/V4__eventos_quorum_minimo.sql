-- V4: quorum minimo de un evento (caso §3.1: el coordinador "supervisa el cumplimiento del quorum minimo").
--
-- Opcional: NULL significa que el evento no define un minimo. No toca filas existentes. La coherencia con los cupos
-- maximos (minimo <= maximo) la valida la API; la base solo exige que, si existe, sea positivo.

alter table eventos add column quorum_minimo integer;
alter table eventos add constraint ck_eventos_quorum_minimo check (quorum_minimo is null or quorum_minimo >= 1);
