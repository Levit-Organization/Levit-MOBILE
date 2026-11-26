# LEVIT Mobile

App Android nativo, feito em Kotlin, do **LEVIT**, um sistema para gerir tabelas personalizadas, equipe, cargos e permissões, recrutamento, automações e finanças de uma empresa. O app conversa com uma API REST própria (CodeIgniter 4 e PostgreSQL).

Projeto Integrador da disciplina de Dispositivos Móveis, IFES Campus Serra, curso Técnico em Informática para Internet.

## Equipe

| Integrantes : Andriy Polastrelli | Cauã Rosa | Guilherme Brandão | Guilherme Villas | Thiago de Aguiar |

## Telas e funcionalidades

O app tem 18 telas ligadas por um único grafo de navegação (`main_graph.xml`).

| Área | O que faz |
|---|---|
| Acesso | Boas-vindas, login, cadastro, recuperar e redefinir senha |
| Módulos e itens | Criar tabelas com campos próprios e cadastrar itens, inclusive arquivos |
| Equipe e cargos | Convidar pessoas e definir permissões de cada cargo por módulo |
| Recrutamento | Candidatos organizados por fase |
| Automações | Regras com condição e ação, e notificações no app |
| Financeiro e relatórios | Lançamentos por categoria e relatórios exportáveis |
| Perfil e configurações | Dados do usuário e preferências |

**Consulta (banco para tela):** Módulos, Equipe, Itens e Financeiro.
**Inserção (tela para banco):** formulário de tabela, itens, cargo, automação e cadastro.

## Tecnologias

| Biblioteca | Para que serve |
|---|---|
| Kotlin e Coroutines | Linguagem e trabalho em segundo plano |
| Material 3 | Componentes visuais |
| Navigation Component | Navegação entre telas |
| RecyclerView | Listas de itens, membros e cargos |
| Retrofit, Gson e OkHttp | Chamadas à API e leitura de JSON |
| Security Crypto | Token de sessão criptografado |

Autenticação: login próprio da API, com token. O Firebase não foi usado, por acordo com a professora.

## Acessibilidade e interface

Contraste mínimo de 4,5:1 (WCAG AA), áreas de toque de pelo menos 44 dp, texto de no mínimo 12 sp, margens do sistema aplicadas (a câmera e a barra de status não cobrem o conteúdo).

## Estrutura do projeto

```
app/src/main/
  java/com/example/levit/
    auth/        telas de acesso
    data/        API, repositórios e tratamento de erros
    ui/          componentes e utilitários de interface
    *Fragment.kt telas do app (uma HostActivity e vários Fragments)
  res/
    layout/      telas e itens de lista
    navigation/  grafo de navegação
    values/      strings, cores, dimensões e estilos
docs/            documentação, prints, MER e prompts
```

## Documentos da entrega

| Item | Arquivo                                                                                                        |
|---|----------------------------------------------------------------------------------------------------------------|
| Modelo entidade-relacionamento | `especificacoes/MER/`                                                                                          |
| Apresentação | `especificacoes/apresentacao/`                                                                                 |
| Uso de IA e prompts | `especificacoes/prompts/`                                                                                      |
| Vídeo (inserção e consulta) | `especificacoes/video/` ou [https://drive.google.com/file/d/1KshiFjZXKEcIeayVFTgpHPh8xPQklEUw/view?usp=sharing] |

## Branches do repositório

| Branch        | Conteúdo |
|---------------|---|
| `master`      | App Android (versão final) |
| `api-backend` | API REST em CodeIgniter 4, para testar o app |

## Como rodar

Para testar o app é preciso subir a API. Ela fica na branch `api-backend`.

### Pré-requisitos

- PHP 8.2 ou mais novo, com as extensões `intl`, `mbstring` e `pgsql`
- Composer
- PostgreSQL
- Android Studio, com emulador ou celular

### 1. Baixe o backend

```
git clone --branch api-backend --single-branch https://github.com/Levit-Organization/Levit-MOBILE.git api-backend
```

Também dá para baixar o ZIP da branch `api-backend` pelo GitHub e extrair.

### 2. Crie o `.env`

Entre na pasta `api-backend` e crie o arquivo `.env` com as credenciais do PostgreSQL (`database.default.*`) e a `JWT_SECRET_KEY`. Peça o arquivo ao grupo. Ele não vai para o GitHub e não deve ser compartilhado em público.

### 3. Instale as dependências

```
composer install
```

Se o banco estiver vazio, rode também `php spark migrate`.

### 4. Suba o backend ouvindo em todas as interfaces

```
php spark serve --host 0.0.0.0 --port 8080
```

Deixe esse terminal aberto enquanto usa o app. O `--host 0.0.0.0` permite que outros dispositivos alcancem o servidor.

### 5. Rode o app pelo Android Studio

Abra o projeto na branch `main`, espere o Gradle sincronizar e aperte Run com o emulador ou o celular conectado. Depois de mudanças grandes, use `Build > Rebuild Project`.

### 6. Permita o acesso à rede local

Na primeira abertura o Android pede permissão de rede local. Toque em **Permitir**. Sem ela, nenhuma chamada ao backend funciona.

### 7. Ajuste o endereço do backend, se precisar

No emulador com o backend na mesma máquina, nada muda: o padrão `10.0.2.2:8080` já funciona. Em celular físico, toque e segure a logo na tela inicial, digite o `host:porta` e toque em **Salvar**. Isso só existe em builds debug.

| Cenário | O que digitar |
|---|---|
| Emulador, backend na mesma máquina | `10.0.2.2:8080` (padrão) |
| Celular físico, mesma rede wifi | IP local do computador, ex.: `192.168.0.23:8080` |

No Windows, o IP local aparece em `ipconfig`, no campo "Endereço IPv4".

## Como usar o sistema

Roteiro para testar tudo em poucos minutos. O menu lateral (ícone de hambúrguer) leva a todas as áreas.

1. **Cadastro e login:** na tela de boas-vindas, toque em cadastrar, preencha os dados e entre. Se esquecer a senha, use "Esqueci minha senha". A sessão dura 8 horas.
2. **Tela inicial:** mostra o resumo da empresa. Dali dá para ir a qualquer área pelo menu.
3. **Criar uma tabela:** em Tabelas, toque em novo módulo, dê um nome e adicione os campos (texto, número, data, arquivo, etc.).
4. **Cadastrar itens:** abra a tabela criada e toque em adicionar. Use a busca e os filtros para encontrar itens, toque em um item para editar e deslize ou use o botão de excluir para remover.
5. **Equipe e cargos:** em Equipe, convide uma pessoa pelo e-mail e crie um cargo. No cargo, escolha para cada tabela o que ele pode ver ou editar.
6. **Recrutamento:** cadastre candidatos e mova cada um entre as fases.
7. **Automações:** crie uma regra com condição e ação. Quando ela dispara, a notificação chega no sino do app.
8. **Financeiro:** adicione lançamentos de entrada e saída, ligados a uma categoria e a um item de uma tabela.
9. **Relatórios:** veja o painel, monte um relatório e gere o PDF.
10. **Configurações:** exporte dados em CSV ou backup, importe planilhas e edite o perfil.
11. **Sair:** use Sair no menu. O app volta para o login e o token é revogado.

## Por que a permissão de rede local existe

A partir da API 36, o Android exige a permissão `ACCESS_LOCAL_NETWORK` para apps abrirem conexões com endereços de rede local (10.x.x.x e 192.168.x.x, incluindo o `10.0.2.2` do emulador). Sem ela, as chamadas dão timeout mesmo com o servidor no ar. A `SplashActivity` pede a permissão sozinha. Em versões antigas do Android o pedido é ignorado.
