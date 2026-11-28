-- KALYNO : suppression definitive des Exercices libres.
-- A coller dans Supabase > SQL Editor. Script rejouable.
--
-- ATTENTION : les exercices libres deja publies sont supprimes pour toujours.
-- Pour en garder une copie, exporte d'abord la table : Table Editor > free_exercises > Export (CSV).
--
-- Ne touche pas aux cours, cartes, chapitres, lecons ni a l'historique des tentatives des eleves.
-- Ne rejoue plus 20261122_exercices_libres.sql ni la partie free_exercises de 20261123_tableaux_a_completer.sql.

begin;

-- Apercu avant suppression (visible dans le resultat si tu retires "begin/commit" pour tester)
-- select count(*) as exercices_libres_supprimes from public.free_exercises;

-- La suppression de la table emporte avec elle ses index, son trigger et ses regles RLS.
drop table if exists public.free_exercises cascade;

drop function if exists public.free_exercises_touch_updated_at();

commit;
