-- Workout Tracker: Supabase schema.
-- Run this once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Safe to run again; it also upgrades the old "exercises" table in place.

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

-- Routines / workout templates
create table if not exists public.workout_days (
  id text primary key,
  name text not null,
  exercise_ids jsonb not null default '[]'::jsonb,
  updated_at bigint not null default 0,
  deleted boolean not null default false,
  server_updated_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint
);

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

do $$
declare t text;
begin
  foreach t in array array['exercises', 'workout_days', 'workout_sessions', 'logged_sets'] loop
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
