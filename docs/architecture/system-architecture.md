# DravyaSetu System Architecture

## High-level architecture

React Web + Flutter Mobile
↓
Public API
↓
Spring Boot Core Backend (Person 2)
├── PostgreSQL
├── AI Service (Person 1, Python/FastAPI)
└── Knowledge Service (Person 3, Python/FastAPI)

## Core rule
Spring Boot is the public application gateway. Frontend clients do not directly access AI, PostgreSQL, RAG, LLM, or other internal service infrastructure.

## Identification flow
Web/Mobile → Spring Boot → AI Service → AIResponse → Spring Boot → Web/Mobile

## Knowledge flow
Web/Mobile → Spring Boot → Knowledge Service → knowledge response → Spring Boot → Web/Mobile

## Shared identity
Supported plant classes and their shared identifiers are defined in `shared/constants/plant-classes.json`.
