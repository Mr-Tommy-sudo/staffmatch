CREATE TABLE candidate_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    city TEXT,
    work_formats JSONB NOT NULL CHECK (jsonb_typeof(work_formats) = 'array'),
    available_hours INTEGER CHECK (available_hours BETWEEN 0 AND 168),
    salary_expectation BIGINT CHECK (salary_expectation >= 0),
    experience_months INTEGER CHECK (experience_months >= 0),
    portfolio_url TEXT,
    skills JSONB NOT NULL CHECK (jsonb_typeof(skills) = 'array'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vacancies (
    id UUID PRIMARY KEY,
    employer_id UUID NOT NULL REFERENCES users(id),
    role TEXT NOT NULL,
    city TEXT,
    work_format TEXT NOT NULL CHECK (work_format IN ('REMOTE', 'HYBRID', 'OFFICE')),
    hours_min INTEGER CHECK (hours_min BETWEEN 0 AND 168),
    salary_from BIGINT CHECK (salary_from >= 0),
    salary_to BIGINT CHECK (salary_to >= 0),
    experience_months_min INTEGER CHECK (experience_months_min >= 0),
    skills JSONB NOT NULL CHECK (jsonb_typeof(skills) = 'array'),
    test_mode TEXT NOT NULL CHECK (test_mode IN ('NONE', 'AUTO', 'CUSTOM')),
    level TEXT,
    duration_minutes INTEGER,
    question_count INTEGER,
    competencies JSONB,
    matching_status TEXT NOT NULL DEFAULT 'PENDING',
    generation_status TEXT NOT NULL DEFAULT 'NOT_REQUIRED',
    ranking_status TEXT NOT NULL DEFAULT 'PENDING',
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (salary_from IS NULL OR salary_to IS NULL OR salary_from <= salary_to)
);
CREATE INDEX vacancies_employer_idx ON vacancies(employer_id);

CREATE TABLE tests (
    id UUID PRIMARY KEY,
    vacancy_id UUID NOT NULL UNIQUE REFERENCES vacancies(id),
    external_test_id TEXT,
    generation_version TEXT NOT NULL,
    estimated_duration_minutes INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE test_questions (
    test_id UUID NOT NULL REFERENCES tests(id),
    question_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    question JSONB NOT NULL,
    PRIMARY KEY (test_id, question_id),
    UNIQUE (test_id, position)
);

CREATE TABLE matches (
    vacancy_id UUID NOT NULL REFERENCES vacancies(id),
    candidate_id UUID NOT NULL REFERENCES users(id),
    eligible BOOLEAN NOT NULL,
    score NUMERIC(5,2) NOT NULL CHECK (score BETWEEN 0 AND 100),
    matched JSONB NOT NULL,
    partial JSONB NOT NULL,
    missing JSONB NOT NULL,
    algorithm_version TEXT NOT NULL,
    input_snapshot JSONB NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vacancy_id, candidate_id)
);

CREATE TABLE test_assignments (
    id UUID PRIMARY KEY,
    vacancy_id UUID NOT NULL REFERENCES vacancies(id),
    candidate_id UUID NOT NULL REFERENCES users(id),
    status TEXT NOT NULL CHECK (status IN ('ASSIGNED', 'SUBMITTED', 'SCORED', 'SCORING_FAILED')),
    answers JSONB,
    scoring_input JSONB,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    UNIQUE (vacancy_id, candidate_id)
);
CREATE INDEX test_assignments_candidate_idx ON test_assignments(candidate_id);

CREATE TABLE assessment_results (
    assignment_id UUID PRIMARY KEY REFERENCES test_assignments(id),
    total_score NUMERIC(5,2) NOT NULL CHECK (total_score BETWEEN 0 AND 100),
    questions JSONB NOT NULL,
    breakdown JSONB NOT NULL,
    summary TEXT,
    scoring_version TEXT NOT NULL,
    model_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ranking_entries (
    vacancy_id UUID NOT NULL REFERENCES vacancies(id),
    candidate_id UUID NOT NULL REFERENCES users(id),
    bucket TEXT NOT NULL CHECK (bucket IN ('FINAL', 'WAITING')),
    rank INTEGER,
    final_score NUMERIC(5,2) CHECK (final_score BETWEEN 0 AND 100),
    state TEXT NOT NULL,
    components JSONB,
    explanation TEXT,
    ranking_version TEXT NOT NULL,
    input_snapshot JSONB NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vacancy_id, candidate_id)
);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    vacancy_id UUID NOT NULL REFERENCES vacancies(id),
    candidate_id UUID NOT NULL REFERENCES users(id),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMPTZ,
    UNIQUE (vacancy_id, candidate_id)
);
CREATE INDEX invitations_candidate_idx ON invitations(candidate_id);
