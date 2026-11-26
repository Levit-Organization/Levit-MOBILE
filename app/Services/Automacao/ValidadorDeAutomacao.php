<?php

namespace App\Services\Automacao;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Models\CampoModuloModel;
use App\Models\ModuloModel;
use App\Services\AutorizacaoModuloService;

/**
 * Validação da definição de uma automação (nome, gatilhos, condições e
 * ações) antes de gravar ou de simular.
 */
class ValidadorDeAutomacao
{
    /** Módulos de dados disparam com registros; vagas, com candidatos. */
    private const GATILHOS_REGISTRO = ['criacao', 'atualizacao', 'exclusao'];
    private const GATILHOS_VAGA     = ['candidato_criado', 'candidato_mudou_etapa'];
    private const GATILHOS_VALIDOS  = [...self::GATILHOS_REGISTRO, ...self::GATILHOS_VAGA];
    private const TIPOS_ACAO_VALIDOS = ['enviar_email', 'webhook', 'notificacao', 'atualizar_campo', 'criar_registro'];
    private const DESTINOS_NOTIFICACAO = ['todos', 'cargo', 'usuario', 'responsavel'];

    public function __construct(
        protected ModuloModel $moduloModel = new ModuloModel(),
        protected CampoModuloModel $campoModuloModel = new CampoModuloModel(),
        protected AutorizacaoModuloService $autorizacaoModuloService = new AutorizacaoModuloService(),
    ) {
    }

    /**
     * Valida nome, gatilhos, condição e ações; devolve os campos prontos para
     * gravar e a lista de ações.
     *
     * @return array{campos: array, acoes: list<array>}
     */
    public function validarDefinicao(string $moduloId, string $empresaId, array $dados): array
    {
        $nome = trim((string) ($dados['nome'] ?? ''));
        if (mb_strlen($nome) < 2 || mb_strlen($nome) > 100) {
            throw new \DomainException('O nome precisa ter entre 2 e 100 caracteres.');
        }

        $ehVaga   = $this->tipoDoModulo($moduloId) === 'recrutamento';
        $gatilhos = self::normalizarGatilhos($dados);

        $permitidos = $ehVaga ? self::GATILHOS_VAGA : self::GATILHOS_REGISTRO;
        foreach ($gatilhos as $gatilho) {
            if (! in_array($gatilho, $permitidos, true)) {
                throw new \DomainException($ehVaga
                    ? 'Em vagas, as automações disparam com candidatos (novo candidato ou mudança de etapa).'
                    : 'Gatilhos de candidato só existem em vagas de recrutamento.');
            }
        }

        $acoes = array_values($dados['acoes'] ?? []);
        if (empty($acoes)) {
            throw new \DomainException('A automação precisa de pelo menos uma ação.');
        }
        foreach ($acoes as $acao) {
            $this->validarAcao($acao, $empresaId, $moduloId, $ehVaga, $gatilhos);
        }

        return [
            'campos' => [
                'nome'      => $nome,
                'gatilho'   => $gatilhos[0], // legado: primeiro gatilho
                'gatilhos'  => $gatilhos,
                'condicoes' => $this->normalizarCondicoes($dados, $moduloId, $ehVaga, $gatilhos),
                // Formato antigo (condição única): substituído por `condicoes`.
                'campo_condicao_id' => null,
                'condicao_operador' => null,
                'condicao_valor'    => null,
                'condicao_valor_de' => null,
            ],
            'acoes' => $acoes,
        ];
    }

    /**
     * Aceita `condicoes` ({ logica, regras }) ou a condição única antiga
     * (campo_condicao_id...). Devolve null quando não há regras.
     */
    public function normalizarCondicoes(array $dados, string $moduloId, bool $ehVaga, array $gatilhos): ?array
    {
        $condicoes = $dados['condicoes'] ?? Condicoes::doFormatoAntigo($dados);
        $regras    = array_values($condicoes['regras'] ?? []);
        if ($regras === []) {
            return null;
        }

        $logica = $condicoes['logica'] ?? 'e';
        if (! in_array($logica, ['e', 'ou'], true)) {
            throw new \DomainException('Use "e" ou "ou" para combinar as condições.');
        }
        if (count($regras) > Condicoes::MAX_REGRAS) {
            throw new \DomainException('Use no máximo ' . Condicoes::MAX_REGRAS . ' condições.');
        }

        $camposValidos = $ehVaga
            ? array_keys(CamposCandidato::CAMPOS)
            : array_column($this->campoModuloModel->select('id')->where('modulo_id', $moduloId)->findAll(), 'id');
        $temAnterior = array_intersect($gatilhos, ['atualizacao', 'candidato_mudou_etapa']) !== [];

        $normalizadas = [];
        foreach ($regras as $i => $regra) {
            $n        = $i + 1;
            $campo    = (string) ($regra['campo'] ?? '');
            $operador = (string) ($regra['operador'] ?? '');
            $valor    = $regra['valor'] ?? null;
            $valorDe  = $regra['valor_de'] ?? null;

            if (! in_array($campo, $camposValidos, true)) {
                throw new \DomainException("Condição {$n}: escolha uma coluna desta tabela.");
            }
            if (! in_array($operador, Condicoes::OPERADORES, true)) {
                throw new \DomainException("Condição {$n}: operador inválido.");
            }
            if (Condicoes::exigeValor($operador) && ($valor === null || $valor === '')) {
                throw new \DomainException("Condição {$n}: informe o valor de comparação.");
            }
            if ($operador === 'mudou_de_para' && ($valorDe === null || $valorDe === '')) {
                throw new \DomainException("Condição {$n}: informe o valor de origem (\"mudou de\").");
            }
            if (in_array($operador, Condicoes::OPERADORES_DE_MUDANCA, true) && ! $temAnterior) {
                throw new \DomainException("Condição {$n}: condições de mudança precisam do gatilho \"" . ($ehVaga ? 'Mudou de etapa' : 'Atualização') . '".');
            }

            $normalizadas[] = [
                'campo'    => $campo,
                'operador' => $operador,
                'valor'    => Condicoes::exigeValor($operador) ? (string) $valor : null,
                'valor_de' => $operador === 'mudou_de_para' ? (string) $valorDe : null,
            ];
        }

        return ['logica' => $logica, 'regras' => $normalizadas];
    }

    public function tipoDoModulo(string $moduloId): ?string
    {
        return $this->moduloModel->select('tipo')->find($moduloId)['tipo'] ?? null;
    }

    /**
     * Aceita `gatilhos` (lista) ou `gatilho` (texto, formato antigo). Remove
     * repetidos e valida cada um; precisa sobrar pelo menos um.
     *
     * @return string[]
     */
    public static function normalizarGatilhos(array $dados): array
    {
        $bruto = $dados['gatilhos'] ?? (isset($dados['gatilho']) ? [$dados['gatilho']] : []);
        if (! is_array($bruto)) {
            $bruto = [$bruto];
        }

        $gatilhos = array_values(array_unique($bruto));
        if ($gatilhos === []) {
            throw new \DomainException('Escolha pelo menos um gatilho.');
        }

        foreach ($gatilhos as $gatilho) {
            if (! is_string($gatilho) || ! in_array($gatilho, self::GATILHOS_VALIDOS, true)) {
                throw new \DomainException('Gatilho inválido: \'' . (is_scalar($gatilho) ? $gatilho : gettype($gatilho)) . '\'.');
            }
        }

        return $gatilhos;
    }

    private function validarAcao(array $acao, string $empresaId, string $moduloId, bool $ehVaga, array $gatilhos): void
    {
        $tipo = $acao['tipo'] ?? null;
        if (! in_array($tipo, self::TIPOS_ACAO_VALIDOS, true)) {
            throw new \DomainException("Tipo de ação inválido: '{$tipo}'.");
        }

        $config = $acao['configuracao'] ?? [];

        if ($tipo === 'enviar_email' && ((empty($config['destinatario_campo_id']) && empty($config['destinatario'])) || empty($config['assunto']) || empty($config['corpo']))) {
            throw new \DomainException('Ação de e-mail precisa de destinatário, assunto e corpo.');
        }

        if ($tipo === 'webhook') {
            if (empty($config['url'])) {
                throw new \DomainException('Informe o endereço do outro sistema.');
            }
            (new DestinoWebhook())->validarFormato((string) $config['url']);
        }

        if ($tipo === 'notificacao') {
            $this->validarNotificacao($config, $empresaId, $moduloId);
        }

        if (in_array($tipo, ['atualizar_campo', 'criar_registro'], true) && $ehVaga) {
            throw new \DomainException('Ações sobre itens não estão disponíveis em vagas.');
        }

        if ($tipo === 'atualizar_campo') {
            if (in_array('exclusao', $gatilhos, true)) {
                throw new \DomainException('"Atualizar coluna" não funciona com o gatilho "Exclusão": o item já não existe.');
            }
            if (empty($config['campo_id']) || ! $this->campoModuloModel->where('id', $config['campo_id'])->where('modulo_id', $moduloId)->first()) {
                throw new \DomainException('Escolha a coluna que será atualizada.');
            }
        }

        if ($tipo === 'criar_registro') {
            $this->validarCriacao($config, $empresaId, $moduloId, $gatilhos);
        }
    }

    private function validarCriacao(array $config, string $empresaId, ?string $moduloId, array $gatilhos): void
    {
        $destinoId = $config['modulo_destino_id'] ?? '';
        $destino   = $destinoId !== '' ? $this->moduloModel->where('id', $destinoId)->where('empresa_id', $empresaId)->first() : null;

        if (! $destino || $destino['tipo'] === 'recrutamento') {
            throw new \DomainException('Escolha uma tabela de dados para criar o item.');
        }

        // A automação grava no destino em nome de quem a criou: essa pessoa
        // precisa poder editar a tabela de destino.
        $usuario = service('authenticatedUser');
        if ($usuario->autenticado()) {
            try {
                $this->autorizacaoModuloService->exigirNivel($usuario->acessoTotal, $usuario->cargoId, $destinoId, 'editar');
            } catch (NaoEncontradoException|AcessoNegadoException) {
                throw new \DomainException('Você precisa poder editar a tabela de destino para criar itens nela.');
            }
        }

        // Ligar o novo item ao que disparou: uma coluna de ligação do destino que aponta para esta tabela.
        $ligar = $config['ligar_campo_id'] ?? null;
        if ($ligar !== null && $ligar !== '') {
            $campo = $this->campoModuloModel->where('id', $ligar)->where('modulo_id', $destinoId)->first();
            if (! $campo || $campo['tipo'] !== 'vinculo' || $campo['modulo_vinculado_id'] !== $moduloId) {
                throw new \DomainException('A coluna escolhida para ligar não aponta para esta tabela.');
            }
            if (in_array('exclusao', $gatilhos, true)) {
                throw new \DomainException('Ligar ao item que disparou não funciona com o gatilho "Exclusão": o item já não existe.');
            }
        }

        $mapeamento = array_filter((array) ($config['mapeamento'] ?? []), static fn ($v) => $v !== null && $v !== '');
        if ($mapeamento === [] && ($ligar === null || $ligar === '')) {
            throw new \DomainException('Preencha pelo menos uma coluna do novo item.');
        }

        $camposDestino = array_column($this->campoModuloModel->select('id')->where('modulo_id', $destinoId)->findAll(), 'id');
        if (array_diff(array_keys($mapeamento), $camposDestino) !== []) {
            throw new \DomainException('Alguma coluna do novo item não existe na tabela de destino.');
        }
    }

    private function validarNotificacao(array $config, string $empresaId, ?string $moduloId = null): void
    {
        $destino = $config['destinatarios'] ?? null;
        if (! in_array($destino, self::DESTINOS_NOTIFICACAO, true)) {
            throw new \DomainException('Escolha quem recebe a notificação.');
        }
        if (trim((string) ($config['titulo'] ?? '')) === '') {
            throw new \DomainException('A notificação precisa de um título.');
        }
        if (mb_strlen((string) $config['titulo']) > 200) {
            throw new \DomainException('O título da notificação pode ter no máximo 200 caracteres.');
        }

        if ($destino === 'todos') {
            return;
        }

        if ($destino === 'responsavel') {
            if ($moduloId === null || ! $this->campoModuloModel->where('modulo_id', $moduloId)->where('tipo', 'responsavel')->first()) {
                throw new \DomainException('Para avisar o responsável, a tabela precisa de uma coluna "Responsável".');
            }

            return;
        }

        $destinoId = $config['destino_id'] ?? '';
        $tabela    = $destino === 'cargo' ? 'cargo' : 'usuario';
        $existe    = $destinoId !== '' && db_connect()->table($tabela)->where('id', $destinoId)->where('empresa_id', $empresaId)->countAllResults() > 0;

        if (! $existe) {
            throw new \DomainException($destino === 'cargo' ? 'Cargo da notificação não encontrado.' : 'Pessoa da notificação não encontrada.');
        }
    }
}
