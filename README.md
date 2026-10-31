# LEVIT Mobile

App Android nativo (Kotlin) do LEVIT, com o fluxo de autenticação (login,
cadastro e logout) integrado ao backend CodeIgniter (`api-backend`), e com
o endereço desse backend configurável dentro do próprio app.

## Passo a passo para rodar e usar o app

### 1. Configure o `.env` do backend

Na pasta `api-backend`, confirme que o `.env` aponta pro PostgreSQL (não
MySQL) com as credenciais certas. Se ainda não rodou, execute:

```
php spark migrate
```

### 2. Suba o backend ouvindo em todas as interfaces

Na pasta `api-backend`, rode:

```
php spark serve --host 0.0.0.0 --port 8080
```

Deixe esse terminal aberto o tempo todo que for usar o app. O
`--host 0.0.0.0` é o que permite outros dispositivos (não só a própria
máquina) alcançarem o servidor.

### 3. Rode o app pelo Android Studio

Com o projeto sincronizado (Gradle Sync já feito), aperte o botão Run com
o emulador ou celular físico conectado. Se tiver feito mudanças recentes
no código, vale um `Build > Rebuild Project` antes.

### 4. Permita o acesso à rede local na primeira abertura

Um popup do Android deve aparecer pedindo permissão de rede local. Toque
em **Permitir**. Sem isso, nenhuma chamada ao backend funciona, mesmo com
o servidor rodando certinho.

### 5. Ajuste o endereço do backend, se necessário

Se for o emulador rodando na mesma máquina do backend, não precisa fazer
nada — o padrão `10.0.2.2:8080` já funciona.

| Cenário | O que digitar |
|---|---|
| Emulador, backend na mesma máquina | `10.0.2.2:8080` (padrão, nada a fazer) |
| Celular físico, mesma rede wifi | IP local do computador, ex.: `192.168.0.23:8080` |
| Outro computador/emulador | Mesma lógica: `10.0.2.2:8080` se o backend estiver na própria máquina desse computador |

Para descobrir o IP local do computador no Windows: `ipconfig` (procure
"Endereço IPv4" na rede ativa).

### 6. Use o app normalmente

Cadastre uma conta nova (ou faça login com uma já existente), navegue
pelas telas protegidas pelo menu lateral, e use o **Sair** quando quiser
encerrar a sessão. O token dura 8 horas; depois disso, pede login de
novo.

## Por que a permissão de rede local (passo 5) existe

A partir da API 36/37, o Android passou a exigir essa permissão
(`ACCESS_LOCAL_NETWORK`) para qualquer app abrir conexões para endereços
de rede local (10.x.x.x, 192.168.x.x, incluindo o `10.0.2.2` do
emulador). Sem ela, as chamadas ao backend falham com timeout mesmo com
o servidor funcionando normalmente — o navegador do sistema não é
afetado da mesma forma, só apps como este. `SplashActivity` pede essa
permissão automaticamente na primeira abertura; em versões mais antigas
do Android, que não têm essa permissão, o pedido é ignorado sem travar o
app.
