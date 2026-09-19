# OBRA360

Aplicativo Android para gestão de obras, ferramentas de construção e comunidade profissional.

## Módulos
- autenticação
- painel
- obras e progresso
- comunidade
- publicação
- perfil
- calculadora de concreto
- arquitetura preparada para Supabase

## Build
`gradle :app:assembleDebug`

O APK de debug é publicado automaticamente pelo GitHub Actions em cada push para `main`.

## Backend
O aplicativo usa o projeto Supabase OBRA360 configurado para autenticação e dados. Chaves privilegiadas não são usadas no aplicativo.
