<?php

namespace App\Controllers;

use App\Exceptions\NaoEncontradoException;
use App\Libraries\SessaoWeb;
use App\Services\AuthService;
use CodeIgniter\HTTP\ResponseInterface;

class AuthController extends BaseApiController
{
    protected AuthService $authService;

    public function __construct()
    {
        $this->authService = new AuthService();
    }

    /** Sempre a mesma resposta, exista ou não a conta. */
    public function esqueciSenha(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        if (! $this->validateData($dados, ['email' => ['required', 'valid_email']])) {
            return $this->respondError('Informe um e-mail válido.', 422, $this->validator->getErrors());
        }

        $this->authService->solicitarRedefinicaoDeSenha($dados['email']);

        return $this->response->setJSON([
            'status'  => 'success',
            'message' => 'Se houver uma conta com esse e-mail, enviamos um link para criar uma senha nova. Confira também o spam.',
        ]);
    }

    public function redefinirSenha(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'token' => ['required', 'regex_match[/^[0-9a-f]{64}$/]'],
            'senha' => ['required', 'min_length[8]', 'regex_match[/^(?=.*[A-Za-z])(?=.*\d).+$/]'],
        ];
        $messages = [
            'token' => ['regex_match' => 'Link de redefinição inválido.'],
            'senha' => ['regex_match' => 'A senha deve conter letras e números.', 'min_length' => 'A senha precisa ter pelo menos 8 caracteres.'],
        ];

        if (! $this->validateData($dados, $rules, $messages)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        try {
            $this->authService->redefinirSenha($dados['token'], $dados['senha']);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->response->setJSON(['status' => 'success', 'message' => 'Senha alterada. Entre com a senha nova.']);
    }

    public function registrar(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'nome'         => ['required', 'min_length[3]', 'max_length[150]'],
            'email'        => ['required', 'valid_email', 'max_length[150]'],
            'senha'        => ['required', 'min_length[8]', 'regex_match[/^(?=.*[A-Za-z])(?=.*\d).+$/]'],
            'cnpj_cpf'     => ['permit_empty', 'max_length[18]'],
            'nome_empresa' => ['required', 'min_length[2]', 'max_length[150]'],
        ];

        $messages = [
            'senha' => [
                'regex_match' => 'A senha deve conter letras e números.',
            ],
        ];

        if (! $this->validateData($dados, $rules, $messages)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        try {
            $resultado = $this->authService->registrarFundador($dados);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 409);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSessao(
            $this->authService->montarRespostaAutenticacao($resultado['usuario'], $resultado['empresa']),
            201
        );
    }


    public function login(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'email' => ['required', 'valid_email'],
            'senha' => ['required'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        try {
            $resultado = $this->authService->autenticar($dados['email'], $dados['senha']);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 401);
        }

        return $this->respondSessao(
            $this->authService->montarRespostaAutenticacao($resultado['usuario'], $resultado['empresa']),
            200
        );
    }

    /** Equipes da pessoa logada, para o seletor do menu. */
    public function equipes(): ResponseInterface
    {
        return $this->respondSuccess($this->authService->equipesDoUsuario(service('authenticatedUser')->id));
    }

    /**
     * Entra em outra equipe da pessoa: o token atual é revogado e um novo,
     * com a equipe escolhida, volta na resposta.
     */
    public function trocarEquipe(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        if (! $this->validateData($dados, ['empresa_id' => ['required', 'regex_match[/^[0-9a-f-]{36}$/i]']])) {
            return $this->respondError('Escolha uma equipe.', 422, $this->validator->getErrors());
        }

        $user = service('authenticatedUser');

        try {
            $empresa = $this->authService->equipeDoUsuario($user->id, $dados['empresa_id']);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        }

        return $this->novaSessao($empresa);
    }

    /** Cria uma equipe nova com a pessoa logada como administradora, e já entra nela. */
    public function criarEquipe(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'nome'     => ['required', 'min_length[2]', 'max_length[150]'],
            'cnpj_cpf' => ['permit_empty', 'max_length[18]'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        try {
            $empresa = $this->authService->criarEquipe(service('authenticatedUser')->id, $dados['nome'], $dados['cnpj_cpf'] ?? null);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 409);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->novaSessao($empresa, 201);
    }

    private function novaSessao(array $empresa, int $status = 200): ResponseInterface
    {
        $user    = service('authenticatedUser');
        $usuario = model(\App\Models\UsuarioModel::class)->find($user->id);

        $this->authService->revogarToken($user->jti, $user->id, $user->expiraEm);

        return $this->respondSessao($this->authService->montarRespostaAutenticacao($usuario, $empresa), $status);
    }

    public function logout(): ResponseInterface
    {
        $user = service('authenticatedUser');

        $this->authService->revogarToken($user->jti, $user->id, $user->expiraEm);
        SessaoWeb::encerrar($this->response);

        return $this->respondSuccess(null, 200);
    }
}