<?php

namespace App\Services\Automacao;

use App\Exceptions\FalhaPermanenteException;
use App\Services\NotificacaoService;

/**
 * Monta cada ação como ela seria executada: destinatários resolvidos,
 * variáveis substituídas e configuração conferida. Não envia nada; serve
 * tanto à execução real quanto à prévia do botão "Testar".
 */
class PreparacaoDeAcoes
{
    /**
     * @param \Closure(): NotificacaoService $notificacoes só é chamado por ações de notificação
     */
    public function __construct(
        private VariaveisDoModulo $variaveis,
        private DestinoWebhook $destinos,
        private \Closure $notificacoes,
    ) {
    }

    /**
     * Resolve a ação para o que seria executado de fato.
     *
     * @throws FalhaPermanenteException quando a configuração não permite executar
     */
    public function preparar(array $acao, array $dados, string $moduloId): array
    {
        $config = $acao['configuracao'] ?? [];

        return match ($acao['tipo']) {
            'enviar_email'    => $this->prepararEmail($config, $dados, $moduloId),
            'webhook'         => $this->prepararWebhook($config, $dados),
            'notificacao'     => $this->prepararNotificacao($config, $dados, $moduloId),
            'atualizar_campo' => $this->prepararAtualizacao($config, $dados, $moduloId),
            'criar_registro'  => $this->prepararCriacao($config, $dados, $moduloId),
            default           => throw new FalhaPermanenteException("Tipo de ação desconhecido: {$acao['tipo']}."),
        };
    }

    private function prepararEmail(array $config, array $dados, string $moduloId): array
    {
        $destinatario = $config['destinatario'] ?? '';
        if ($destinatario === '' && ! empty($config['destinatario_campo_id'])) {
            $campoId = $config['destinatario_campo_id'];
            // Coluna "Responsável": o e-mail vai para a conta da pessoa.
            $destinatario = $this->variaveis->tipoDoCampo($moduloId, $campoId) === 'responsavel'
                ? (string) ($this->variaveis->responsavelDoItem($moduloId, $dados, $campoId)['email'] ?? '')
                : (string) ($dados[$campoId] ?? '');
        }
        $destinatario = trim($this->variaveis->substituir($destinatario, $moduloId, $dados));

        if ($destinatario === '') {
            throw new FalhaPermanenteException('Não foi possível determinar o destinatário do e-mail.');
        }
        if (! filter_var($destinatario, FILTER_VALIDATE_EMAIL)) {
            throw new FalhaPermanenteException("Destinatário inválido: '{$destinatario}'.");
        }

        return [
            'tipo'         => 'enviar_email',
            'destinatario' => $destinatario,
            'assunto'      => $this->variaveis->substituir($config['assunto'] ?? '', $moduloId, $dados),
            'corpo'        => $this->variaveis->substituir($config['corpo'] ?? '', $moduloId, $dados),
        ];
    }

    private function prepararWebhook(array $config, array $dados): array
    {
        $url = trim((string) ($config['url'] ?? ''));
        try {
            $this->destinos->validarFormato($url);
        } catch (\DomainException $e) {
            throw new FalhaPermanenteException($e->getMessage(), 0, $e);
        }

        return ['tipo' => 'webhook', 'url' => $url, 'payload' => $dados];
    }

    /**
     * Resolve os destinatários (só quem tem acesso ao módulo) e o texto. A prévia
     * mostra quantas pessoas receberiam.
     */
    private function prepararNotificacao(array $config, array $dados, string $moduloId): array
    {
        $modulo  = $this->variaveis->modulo($moduloId);
        $destino = $config['destinatarios'] ?? 'todos';
        $alvoId  = ($config['destino_id'] ?? '') ?: null;

        if ($destino === 'responsavel') {
            $responsavel = $this->variaveis->responsavelDoItem($moduloId, $dados);
            if ($responsavel === null) {
                throw new FalhaPermanenteException('O item não tem responsável para avisar.');
            }
            $destino = 'usuario';
            $alvoId  = $responsavel['id'];
        }

        // Mesmo indicado como responsável, só recebe quem enxerga a tabela.
        $usuarios = ($this->notificacoes)()->usuariosComAcessoAoModulo(
            $moduloId,
            $modulo['empresa_id'],
            $destino === 'cargo' ? $alvoId : null,
            $destino === 'usuario' ? $alvoId : null
        );

        if ($usuarios === []) {
            throw new FalhaPermanenteException('Nenhum destinatário com acesso a esta tabela.');
        }

        $titulo = trim($this->variaveis->substituir($config['titulo'] ?? '', $moduloId, $dados));
        if ($titulo === '') {
            throw new FalhaPermanenteException('O título da notificação ficou vazio.');
        }

        return [
            'tipo'          => 'notificacao',
            'destinatarios' => count($usuarios),
            'usuario_ids'   => $usuarios,
            'empresa_id'    => $modulo['empresa_id'],
            'titulo'        => mb_substr($titulo, 0, 200),
            'mensagem'      => $this->variaveis->substituir($config['mensagem'] ?? '', $moduloId, $dados) ?: null,
            'link'          => $modulo['tipo'] === 'recrutamento' ? '/recrutamento' : "/modulos/{$moduloId}/registros",
        ];
    }

    /** Muda um campo do próprio registro que disparou a automação. */
    private function prepararAtualizacao(array $config, array $dados, string $moduloId): array
    {
        $campoId = $config['campo_id'] ?? '';
        $nome    = $this->variaveis->camposDoModulo($moduloId)[$campoId] ?? null;
        if ($nome === null) {
            throw new FalhaPermanenteException('A coluna a atualizar não existe mais nesta tabela.');
        }

        return [
            'tipo'     => 'atualizar_campo',
            'campo_id' => $campoId,
            'campo'    => $nome,
            'valor'    => $this->variaveis->substituir((string) ($config['valor'] ?? ''), $moduloId, $dados),
        ];
    }

    /** Cria um registro em outro módulo, com os valores montados a partir deste. */
    private function prepararCriacao(array $config, array $dados, string $moduloId): array
    {
        $destinoId = $config['modulo_destino_id'] ?? '';
        $destino   = $this->variaveis->modulo($destinoId);

        if ($destino['empresa_id'] !== $this->variaveis->modulo($moduloId)['empresa_id'] || $destino['tipo'] === 'recrutamento') {
            throw new FalhaPermanenteException('A tabela de destino não pode receber itens desta automação.');
        }

        $camposDestino = $this->variaveis->camposDoModulo($destinoId);
        $valores       = [];
        $legiveis      = [];

        foreach ($config['mapeamento'] ?? [] as $campoId => $modelo) {
            if (! isset($camposDestino[$campoId])) {
                continue; // campo removido do destino depois de configurar
            }
            $valor = $this->variaveis->substituir((string) $modelo, $moduloId, $dados);
            if ($valor === '') {
                continue;
            }
            $valores[$campoId]                  = $valor;
            $legiveis[$camposDestino[$campoId]] = $valor;
        }

        // Coluna de ligação que recebe o item que disparou (some se a coluna foi excluída).
        $ligar = $config['ligar_campo_id'] ?? null;
        $ligar = is_string($ligar) && isset($camposDestino[$ligar]) ? $ligar : null;
        if ($ligar !== null) {
            $legiveis[$camposDestino[$ligar]] = '(o item que disparou a automação)';
        }

        if ($valores === [] && $ligar === null) {
            throw new FalhaPermanenteException('Nenhuma coluna do novo item ficou preenchida.');
        }

        return [
            'tipo'              => 'criar_registro',
            'modulo_destino_id' => $destinoId,
            'modulo'            => $destino['nome'],
            'valores'           => $valores,
            'ligar_campo_id'    => $ligar,
            'dados'             => $legiveis,
        ];
    }
}
