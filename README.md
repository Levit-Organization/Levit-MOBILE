# LEVIT Mobile

App Android nativo (Kotlin) do LEVIT, com o fluxo de autenticação (login,
cadastro e logout) integrado ao backend CodeIgniter (`api-backend`).

## Pré-requisitos

- Android Studio com o projeto sincronizado (Gradle Sync).
- PHP e o backend (`api-backend`) rodando localmente.
- Um emulador Android, ou um celular físico com depuração USB habilitada.

## Como rodar localmente

1. **Suba o backend**, na pasta `api-backend`:

   ```
   php spark serve
   ```

   Por padrão ele sobe em `http://localhost:8080`. Deixe esse terminal
   aberto enquanto for testar o app.

2. **Crie o túnel do ADB** entre o dispositivo (emulador ou celular físico
   via USB) e a sua máquina. Isso precisa ser refeito toda vez que o
   emulador é reiniciado ou o celular é reconectado:

   ```
   adb reverse tcp:8080 tcp:8080
   ```

   Sem esse comando, o app não consegue alcançar o backend, mesmo com ele
   rodando normalmente. Se tiver mais de um dispositivo/emulador
   conectado ao mesmo tempo, rode o `adb reverse` no dispositivo certo
   com `adb -s <id_do_dispositivo> reverse tcp:8080 tcp:8080` (veja os
   ids com `adb devices`).

3. **Rode o app** pelo Android Studio normalmente (botão Run).

O app está configurado para falar com `http://localhost:8080/api/v1/`
(`ApiClient.kt`), usando o túnel do passo 2. Não é preciso mexer em
firewall nem descobrir IP de rede local, o `adb reverse` já resolve isso
da mesma forma em qualquer máquina (Windows, Mac ou Linux).

## Fluxo de autenticação implementado

- **Login** (`LoginActivity`) e **cadastro** (`CadastroActivity`) chamam o
  backend de verdade, através de `AuthRepository` (`signIn`/`signUp`).
- O token JWT recebido é salvo de forma criptografada
  (`SessionManager`, via `EncryptedSharedPreferences`), junto com os
  dados do usuário e da empresa.
- **Rotas protegidas**: `SplashActivity` decide entre abrir o Dashboard
  direto ou pedir login de novo, olhando se existe um token salvo e
  ainda válido. Telas internas (`HamburgerMenuBaseActivity`) fazem a
  mesma checagem e redirecionam para o login se a sessão expirou.
- **Logout** é feito pelo item "Sair" do menu lateral, que chama
  `AuthRepository.signOut()`.
- O JWT dura 8 horas e não é renovado automaticamente (o backend não
  tem endpoint de refresh); depois disso, é pedido login de novo.

## Limitações conhecidas / pendências

1. O backend ainda não implementa `POST /auth/forgot-password` e
   `POST /auth/reset-password`. A tela de recuperação de senha
   (`RecuperarSenhaActivity`) já chama esses endpoints, mas recebe 404
   até o backend implementá-los.
2. Não existe uma segunda tela para o usuário definir a nova senha após
   clicar no link de recuperação (o layout `activity_recuperar_senha2.xml`
   existe, mas não tem uma Activity associada ainda).
3. A tela de cadastro não tem campo próprio de nome da empresa; hoje o
   app reaproveita o nome da pessoa (`nome_empresa = nome`).
4. A validação de CPF (`isCpfValido`) não aceita CNPJ (14 dígitos), mesmo
   o campo se chamando "CPF ou CNPJ".
5. A proteção de sessão (`HamburgerMenuBaseActivity`) cobre Dashboard e
   Módulos, mas não a `NovoModuloActivity`, que não herda dessa classe.
6. O item "Sair" do menu lateral usa um ícone provisório (`ic_person`),
   por falta de um ícone de logout entre os drawables do projeto.
