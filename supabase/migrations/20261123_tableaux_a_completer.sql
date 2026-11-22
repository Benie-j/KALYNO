-- KALYNO : exercice « Tableau à compléter » (kind = 'table').
-- A coller dans Supabase > SQL Editor. Script REJOUABLE et NON DESTRUCTIF :
--   * aucune ligne n'est modifiee ni supprimee, aucune table n'est creee ;
--   * il ajoute la colonne admin_reference si elle manque (cards et free_exercises) ;
--   * il elargit la liste des types autorises (kind) pour y ajouter 'table' (les types deja presents sont conserves) ;
--   * il refuse seulement les FUTURS tableaux sans reponses (contrainte NOT VALID : les anciennes lignes ne sont pas verifiees).
--
-- Convention de donnees (voir TableExercise.kt cote app, tableExercise.ts cote admin) :
--   kind            = 'table'
--   front           = {"type":"kalyno_table_v1","statement":"...","rows":[[{"type":"text","value":"..."},{"type":"blank","key":"r2c2"}]]}
--   admin_reference = {"type":"kalyno_table_answers_v1","answers":{"r2c2":["reponse","variante"]}}
-- `front` ne contient JAMAIS de reponse. Les reponses sont uniquement dans admin_reference.
--
-- SECURITE : cette migration ne change AUCUNE politique RLS. Comme pour les cartes « saisie libre »,
-- l'application lit admin_reference avec la cle publique pour corriger sur l'appareil, y compris hors ligne.
-- Voir la section « Limites » du compte rendu avant d'y mettre des reponses a fort enjeu.

begin;

-- 1) Colonne des reponses attendues (existe peut-etre deja : ne change rien dans ce cas)
alter table public.cards          add column if not exists admin_reference text;
alter table public.free_exercises add column if not exists admin_reference text;

-- 2) public.cards : autoriser kind = 'table'
do $$
declare
  r        record;
  col_type record;
  kinds    text;
  dropped  boolean := false;
begin
  select data_type, udt_schema, udt_name into col_type
  from information_schema.columns
  where table_schema = 'public' and table_name = 'cards' and column_name = 'kind';

  if col_type.data_type = 'USER-DEFINED' then
    -- `kind` est une enumeration : on ajoute simplement la valeur
    execute format('alter type %I.%I add value if not exists %L', col_type.udt_schema, col_type.udt_name, 'table');
  else
    -- Contraintes CHECK qui listent les types (kind in (...)) et n'autorisent pas encore 'table'
    for r in
      select c.conname
      from pg_constraint c
      where c.conrelid = 'public.cards'::regclass
        and c.contype = 'c'
        and pg_get_constraintdef(c.oid) ~* '^check \(+kind (= any|in |= ''|<> )'
        and pg_get_constraintdef(c.oid) not ilike '%''table''%'
    loop
      execute format('alter table public.cards drop constraint %I', r.conname);
      raise notice 'Contrainte % remplacee (elle refusait kind = table).', r.conname;
      dropped := true;
    end loop;

    if dropped then
      -- Types autorises = les types d'origine + ceux deja presents dans les donnees + 'table'
      select string_agg(quote_literal(k), ', ' order by k) into kinds
      from (
        select unnest(array['qcm', 'flashcard', 'cloze', 'table']) as k
        union
        select distinct kind from public.cards where kind is not null
      ) t;
      execute format('alter table public.cards add constraint cards_kind_ok check (kind in (%s))', kinds);
    end if;
  end if;
end $$;

-- 3) public.free_exercises : la contrainte est connue (20261122_exercices_libres.sql)
alter table public.free_exercises drop constraint if exists free_exercises_kind_ok;
alter table public.free_exercises
  add constraint free_exercises_kind_ok check (kind in ('qcm', 'flashcard', 'cloze', 'table'));

-- 4) Un tableau sans reponses ne peut pas etre enregistre (ne verifie que les nouvelles lignes)
alter table public.cards drop constraint if exists cards_table_has_answers;
alter table public.cards
  add constraint cards_table_has_answers
  check (kind::text <> 'table' or (admin_reference is not null and char_length(btrim(admin_reference::text)) > 0)) not valid;

alter table public.free_exercises drop constraint if exists free_exercises_table_has_answers;
alter table public.free_exercises
  add constraint free_exercises_table_has_answers
  check (kind <> 'table' or (admin_reference is not null and char_length(btrim(admin_reference)) > 0)) not valid;

commit;

-- Verification (a lancer a part) :
--   select conname, pg_get_constraintdef(oid) from pg_constraint
--   where conrelid in ('public.cards'::regclass, 'public.free_exercises'::regclass) and contype = 'c';
