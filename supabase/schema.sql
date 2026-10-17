-- Workout Tracker: Supabase schema.
-- Run this once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Safe to run again; it upgrades existing tables in place. Re-run it after updating the app.

-- Sets server_updated_at on every insert/update, so devices can pull only what changed.
create or replace function public.set_server_updated_at() returns trigger
language plpgsql as $$
begin
  new.server_updated_at := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  return new;
end;
$$;

-- Exercises (existing table is upgraded with the sync columns)
create table if not exists public.exercises (
  id text primary key,
  name text not null,
  muscle_group text not null
);
alter table public.exercises add column if not exists updated_at bigint not null default 0;
alter table public.exercises add column if not exists deleted boolean not null default false;
alter table public.exercises add column if not exists server_updated_at bigint not null
  default (extract(epoch from clock_timestamp()) * 1000)::bigint;
-- Target rep range and weight step used for weight/reps suggestions
alter table public.exercises add column if not exists rep_min integer not null default 8;
alter table public.exercises add column if not exists rep_max integer not null default 12;
alter table public.exercises add column if not exists weight_increment double precision not null default 2.5;
-- Planned sets per workout and rest time between sets
alter table public.exercises add column if not exists target_sets integer not null default 3;
alter table public.exercises add column if not exists rest_seconds integer not null default 90;
-- Barbell lifts get a plate calculator and warm-ups; setup note e.g. "seat height 4"
alter table public.exercises add column if not exists is_barbell boolean not null default false;
alter table public.exercises add column if not exists setup_note text not null default '';

-- Routines / workout templates
create table if not exists public.workout_days (
  id text primary key,
  name text not null,
  exercise_ids jsonb not null default '[]'::jsonb,
  updated_at bigint not null default 0,
  deleted boolean not null default false,
  server_updated_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint
);

-- Superset pairs, e.g. [["a","b"]]
alter table public.workout_days add column if not exists supersets jsonb not null default '[]'::jsonb;

-- Performed workouts
create table if not exists public.workout_sessions (
  id text primary key,
  workout_day_id text,
  name text not null,
  exercise_ids jsonb not null default '[]'::jsonb,
  started_at bigint not null,
  finished_at bigint,
  updated_at bigint not null default 0,
  deleted boolean not null default false,
  server_updated_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint
);

-- Logged sets
create table if not exists public.logged_sets (
  id text primary key,
  session_id text not null,
  exercise_id text not null,
  weight double precision not null,
  reps integer not null,
  performed_at bigint not null,
  updated_at bigint not null default 0,
  deleted boolean not null default false,
  server_updated_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint
);

alter table public.workout_sessions add column if not exists supersets jsonb not null default '[]'::jsonb;

-- Per-workout note on an exercise; id = "<session_id>:<exercise_id>"
create table if not exists public.session_notes (
  id text primary key,
  session_id text not null,
  exercise_id text not null,
  note text not null,
  updated_at bigint not null default 0,
  deleted boolean not null default false,
  server_updated_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint
);

-- Body weight measurements (kg)
create table if not exists public.body_weights (
  id text primary key,
  measured_at bigint not null,
  weight double precision not null,
  updated_at bigint not null default 0,
  deleted boolean not null default false,
  server_updated_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint
);

-- How an exercise went in a workout (GOOD / OKAY / BAD); id = "<session_id>:<exercise_id>"
create table if not exists public.exercise_ratings (
  id text primary key,
  session_id text not null,
  exercise_id text not null,
  rating text not null,
  updated_at bigint not null default 0,
  deleted boolean not null default false,
  server_updated_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint
);

do $$
declare t text;
begin
  foreach t in array array['exercises', 'workout_days', 'workout_sessions', 'logged_sets', 'exercise_ratings', 'session_notes', 'body_weights'] loop
    execute format('drop trigger if exists set_server_updated_at on public.%I', t);
    execute format('create trigger set_server_updated_at before insert or update on public.%I
                    for each row execute function public.set_server_updated_at()', t);
    execute format('create index if not exists %I on public.%I (server_updated_at)', t || '_server_updated_at_idx', t);
    -- Single-user app without login: the anon key may read and write these tables.
    -- Keep your anon key private (don't commit it or share builds that contain it).
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists "anon full access" on public.%I', t);
    execute format('create policy "anon full access" on public.%I for all to anon, authenticated using (true) with check (true)', t);
  end loop;
end;
$$;
