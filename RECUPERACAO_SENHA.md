# Recuperação de senha no Android

A nova `auth/RedefinirSenhaActivity.kt` usa `activity_recuperar_senha2.xml` com
ViewBinding. Inclui confirmação de senha, quatro barras de força, envio à API,
erros de conexão, link inválido/expirado e retorno ao login após o sucesso.

## Como usar os projetos atualizados

1. Atualize o mobile e o backend com os arquivos dos respectivos ZIPs.
2. Mantenha a configuração do banco, JWT e e-mail no `.env` local do backend.
   Rode `composer install` e `php spark migrate --all` se necessário.
3. Execute o backend: `php spark serve --host 0.0.0.0 --port 8080`.
4. No emulador, o app usa `http://10.0.2.2:8080/api/v1/`.
   No celular físico, pressione o logo da tela inicial e informe o IP do
   computador, por exemplo `192.168.0.10:8080`. Os dispositivos precisam
   conseguir alcançar esse servidor.
5. No login do aplicativo, toque em **Redefinir**, informe seu e-mail e envie.
6. O e-mail solicitado pelo mobile contém `levit://reset-password?token=...`.
   Com o aplicativo instalado, esse destino abre a nova Activity.
7. Digite a nova senha e a confirmação. Após o sucesso, faça login novamente.

A senha precisa ter pelo menos oito caracteres, letras e números. Maiúsculas
e símbolos melhoram o indicador de força, mas não são obrigatórios para a API.
O token é conferido localmente quanto ao formato e pelo servidor quanto à
validade, expiração e uso anterior. As senhas digitadas não são salvas no estado
da tela. Requisições em andamento bloqueiam novos envios.

## O ajuste de link no backend

O mobile envia `{ "email": "usuario@empresa.com", "plataforma": "mobile" }`
para `POST /api/v1/auth/forgot-password`. O backend escolhe o destino a partir
da plataforma, sem aceitar uma URL de redirecionamento enviada pelo cliente.
Clientes que omitem a plataforma continuam recebendo o link web.

O destino mobile padrão já funciona com o Manifest entregue. Se desejar,
configure no `.env` do backend:

```dotenv
MOBILE_RESET_PASSWORD_URL = "levit://reset-password"
```

Para desenvolvimento sem envio real de e-mail, use `EMAIL_PROVIDER = log`.
O e-mail simulado fica em `writable/logs/ultimo-email.html`; copie o endereço do
link e abra-o no dispositivo em que o app está instalado. O uso de outro
provedor continua seguindo a configuração de e-mail existente.

A definição da nova senha é enviada para `POST /api/v1/auth/reset-password`:

```json
{ "token": "token de 64 caracteres recebido no link", "senha": "NovaSenha123!" }
```

`UsuarioModel` também foi ajustado para permitir gravar `senha_alterada_em`,
como o serviço e o filtro já esperavam. Assim, sessões anteriores à alteração
da senha deixam de acessar a API. É necessária a migração desse campo, já
incluída no projeto original.

## Testar a abertura da Activity

Use um token real e ainda válido, obtido de uma solicitação de recuperação:

```bash
adb shell am start -a android.intent.action.VIEW -d "levit://reset-password?token=TOKEN_REAL" com.example.levit
```

A abertura da tela não valida o token no servidor. A validade é confirmada ao
enviar a nova senha. Tokens inválidos ou usados oferecem a opção de pedir
outro link. Um link web de recuperação antigo continua abrindo o frontend web;
solicite um novo pelo aplicativo para receber o destino mobile.

## Arquivos principais

- Mobile: `auth/RedefinirSenhaActivity.kt`, `auth/ValidacaoRedefinicaoSenha.kt`,
  `activity_recuperar_senha2.xml`, `AuthApi.kt`, `AuthDtos.kt`, `AuthRepository.kt`,
  `RecuperarSenhaActivity.kt`, `AndroidManifest.xml`, strings e cores.
- `ServerConfig.kt` foi incluído porque já era referenciado pelo projeto, mas
  estava ausente. A configuração por toque longo no logo também foi conectada.
- `ApiClient.kt` passou a registrar apenas informações básicas das chamadas,
  evitando gravar senhas e tokens presentes nos corpos das requisições.
- Backend: `AuthController.php`, `AuthService.php`,
  `Services/Email/LinkRedefinicaoSenha.php` e `UsuarioModel.php`.
- Testes: `ValidacaoRedefinicaoSenhaTest.kt`, `ServerConfigTest.kt` e
  `tests/unit/LinkRedefinicaoSenhaTest.php` no backend.

## Testes e compilação

No mobile: `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
No backend: `vendor/bin/phpunit --configuration phpunit.dist.xml`.
Os arquivos de configuração originais do mobile usam SDK 37, AGP 9.3.2,
Gradle 9.5.0 e critério de daemon Java 25. Use uma instalação compatível do
Android Studio/SDK para esses valores.

### Verificação desta entrega

- Compilação Android `assembleDebug` concluída e 10 testes unitários do app
  aprovados pelo Gradle.
- Backend: 114 testes unitários, com 258 asserções, aprovados.
- Fluxo de recuperação: 19 verificações HTTP aprovadas, incluindo destino
  mobile/web, expiração, uso único do token, troca da senha e invalidação da
  sessão anterior.

A compilação foi feita em uma cópia de validação com Java 17, sem o critério
local de daemon Java 25. Os arquivos de configuração do projeto entregue
foram preservados. A tela ainda precisa de conferência visual em emulador ou
celular; não foi executado um teste de interface neste ambiente.

Para distribuição pública com um domínio próprio, a evolução recomendada é
usar Android App Links HTTPS verificados. Nesse caso, configure o domínio no
Manifest e publique o `assetlinks.json` correspondente antes de mudar
`MOBILE_RESET_PASSWORD_URL`. Alterar apenas essa variável para HTTPS não
registra o domínio no aplicativo.

Referência: https://developer.android.com/training/app-links
