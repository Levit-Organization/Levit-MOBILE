<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\NotificacaoModel;

class NotificacaoService
{
    protected NotificacaoModel $notificacaoModel;

    public function __construct()
    {
        $this->notificacaoModel = new NotificacaoModel();
    }

    /**
     * Cria a notificação para cada usuário. Com `agrupador`, se o usuário já tem
     * uma não lida igual, ela é atualizada (contador + 1, volta ao topo) em vez de
     * empilhar outra. `titulo_agrupado` aceita {n} para o total.
     *
     * @param string[] $usuarioIds
     * @param array{tipo: string, titulo: string, mensagem?: ?string, link?: ?string, agrupador?: ?string, titulo_agrupado?: ?string} $notificacao
     *
     * @return int quantas notificações foram criadas ou atualizadas
     */
    public function notificar(array $usuarioIds, string $empresaId, array $notificacao): int
    {
        $usuarioIds = array_values(array_unique(array_filter($usuarioIds)));
        if ($usuarioIds === []) {
            return 0;
        }

        $agrupador = $notificacao['agrupador'] ?? null;
        $agora     = date('Y-m-d H:i:s');
        $existentes = [];

        if ($agrupador !== null) {
            foreach ($this->notificacaoModel
                ->select('id, usuario_id, contador')
                ->whereIn('usuario_id', $usuarioIds)
                ->where('agrupador', $agrupador)
                ->where('lida_em', null)
                ->findAll() as $linha) {
                $existentes[$linha['usuario_id']] = $linha;
            }
        }

        // No máximo duas instruções, qualquer que seja o número de pessoas:
        // um UPDATE para quem já tinha a notificação agrupada e um INSERT em
        // lote para o resto (antes era uma ida ao banco por pessoa).
        $db = db_connect();
        $db->transStart();

        if ($existentes !== []) {
            $modelo = mb_substr($notificacao['titulo_agrupado'] ?? $notificacao['titulo'], 0, 200);
            $ids    = array_column($existentes, 'id');
            $db->query(
                'UPDATE notificacao
                    SET contador  = contador + 1,
                        titulo    = LEFT(REPLACE(?, \'{n}\', (contador + 1)::text), 200),
                        mensagem  = ?,
                        criado_em = ?
                  WHERE id IN ?',
                [$modelo, $notificacao['mensagem'] ?? null, $agora, $ids]
            );
        }

        $novos = array_values(array_diff($usuarioIds, array_keys($existentes)));
        if ($novos !== []) {
            helper('uuid');
            $db->table('notificacao')->insertBatch(array_map(static fn (string $usuarioId) => [
                'id'         => generate_uuid_v7(),
                'empresa_id' => $empresaId,
                'usuario_id' => $usuarioId,
                'tipo'       => $notificacao['tipo'],
                'titulo'     => mb_substr($notificacao['titulo'], 0, 200),
                'mensagem'   => $notificacao['mensagem'] ?? null,
                'link'       => $notificacao['link'] ?? null,
                'agrupador'  => $agrupador,
                'contador'   => 1,
                'criado_em'  => $agora,
            ], $novos));
        }

        $db->transComplete();

        return count($usuarioIds);
    }

    /**
     * Notificar nunca pode derrubar a ação principal (candidatura, convite...):
     * qualquer falha só vai para o log.
     */
    public function notificarSemFalhar(array $usuarioIds, string $empresaId, array $notificacao): void
    {
        try {
            $this->notificar($usuarioIds, $empresaId, $notificacao);
        } catch (\Throwable $e) {
            log_message('error', 'Falha ao criar notificação: {erro}', ['erro' => $e->getMessage()]);
        }
    }

    /**
     * Usuários da empresa que enxergam o módulo (acesso total ou algum nível
     * concedido ao cargo). Opcionalmente restrito a um cargo ou a um usuário.
     *
     * @return string[]
     */
    public function usuariosComAcessoAoModulo(string $moduloId, string $empresaId, ?string $cargoId = null, ?string $usuarioId = null): array
    {
        $query = db_connect()->table('usuario_equipe u')
            ->select('u.id')
            ->join('cargo c', 'c.id = u.cargo_id')
            ->join('empresa e', 'e.id = u.empresa_id')
            ->join('cargo_modulo_permissao p', 'p.cargo_id = u.cargo_id AND p.modulo_id = ' . db_connect()->escape($moduloId), 'left')
            ->where('u.empresa_id', $empresaId)
            ->groupStart()
                // Acesso total: pelo cargo ou por ser quem criou a empresa.
                ->where('(c.acesso_total OR e.administrador_principal_id = u.id)', null, false)
                ->orWhere('p.cargo_id IS NOT NULL', null, false)
            ->groupEnd();

        if ($cargoId !== null) {
            $query->where('u.cargo_id', $cargoId);
        }
        if ($usuarioId !== null) {
            $query->where('u.id', $usuarioId);
        }

        return array_column($query->get()->getResultArray(), 'id');
    }

    /** Usuários com acesso total da empresa (administradores). */
    public function administradores(string $empresaId): array
    {
        return array_column(
            db_connect()->table('usuario_equipe u')
                ->select('u.id')
                ->join('cargo c', 'c.id = u.cargo_id')
                ->join('empresa e', 'e.id = u.empresa_id')
                ->where('u.empresa_id', $empresaId)
                ->where('(c.acesso_total OR e.administrador_principal_id = u.id)', null, false)
                ->get()
                ->getResultArray(),
            'id'
        );
    }

    /**
     * Mais recentes primeiro, paginado por cursor (`antes_de` = `criado_em` da
     * última notificação da página anterior).
     */
    public function listar(string $usuarioId, ?string $antesDe, int $limite, bool $apenasNaoLidas): array
    {
        $limite = max(1, min($limite, 50));
        // A contagem de não lidas vem na mesma consulta (subconsulta escalar).
        $query = $this->notificacaoModel
            ->select('id, tipo, titulo, mensagem, link, contador, lida_em, criado_em')
            ->select('(SELECT COUNT(*) FROM notificacao n2 WHERE n2.usuario_id = ' . db_connect()->escape($usuarioId) . ' AND n2.lida_em IS NULL) AS nao_lidas_total', false)
            ->where('usuario_id', $usuarioId);

        if ($apenasNaoLidas) {
            $query->where('lida_em', null);
        }
        if ($antesDe) {
            $query->where('criado_em <', $antesDe);
        }

        $linhas  = $query->orderBy('criado_em', 'DESC')->orderBy('id', 'DESC')->limit($limite + 1)->findAll();
        $temMais = count($linhas) > $limite;
        $itens   = array_slice($linhas, 0, $limite);
        $ultima  = end($itens);
        // Página vazia não traz a subconsulta; aí conta separado.
        $naoLidas = $linhas !== [] ? (int) $linhas[0]['nao_lidas_total'] : $this->contarNaoLidas($usuarioId);
        $itens    = array_map(static function (array $n) {
            unset($n['nao_lidas_total']);

            return $n;
        }, $itens);

        return [
            'notificacoes' => $itens,
            'proximo'      => $temMais && $ultima ? $ultima['criado_em'] : null,
            'nao_lidas'    => $naoLidas,
        ];
    }

    public function contarNaoLidas(string $usuarioId): int
    {
        return $this->notificacaoModel->where('usuario_id', $usuarioId)->where('lida_em', null)->countAllResults();
    }

    /** Uma instrução só: o filtro por usuário já garante que a notificação é dele. */
    public function marcarLida(string $notificacaoId, string $usuarioId): void
    {
        $db = db_connect();
        $db->query(
            'UPDATE notificacao SET lida_em = COALESCE(lida_em, ?) WHERE id = ? AND usuario_id = ?',
            [date('Y-m-d H:i:s'), $notificacaoId, $usuarioId]
        );

        if ($db->affectedRows() === 0) {
            throw new NaoEncontradoException('Notificação não encontrada.');
        }
    }

    public function marcarTodasLidas(string $usuarioId): int
    {
        $this->notificacaoModel
            ->where('usuario_id', $usuarioId)
            ->where('lida_em', null)
            ->set('lida_em', date('Y-m-d H:i:s'))
            ->update();

        return db_connect()->affectedRows();
    }

    public function excluir(string $notificacaoId, string $usuarioId): void
    {
        $db = db_connect();
        $db->query('DELETE FROM notificacao WHERE id = ? AND usuario_id = ?', [$notificacaoId, $usuarioId]);

        if ($db->affectedRows() === 0) {
            throw new NaoEncontradoException('Notificação não encontrada.');
        }
    }

    /** Retenção: apaga as lidas há mais de `$dias` dias. */
    public function limparLidasAntigas(int $dias = 90): int
    {
        $this->notificacaoModel
            ->where('lida_em <', date('Y-m-d H:i:s', strtotime("-{$dias} days")))
            ->delete();

        return db_connect()->affectedRows();
    }
}
