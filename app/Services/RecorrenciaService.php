<?php

namespace App\Services;

use CodeIgniter\Events\Events;

/**
 * Itens que se repetem, como a obrigação mensal de um cliente. A tabela tem
 * uma coluna "Repetição" (tipo `recorrencia`); quando um item com repetição é
 * concluído (uma coluna de lista vai para Entregue, Pago, Concluído…), nasce
 * a próxima ocorrência:
 *
 * - o prazo avança pelo período a partir do prazo anterior, não de hoje;
 * - a coluna de status volta para a primeira opção que não encerra o trabalho;
 * - responsável, repetição e as demais colunas são copiados.
 *
 * Cada item gera no máximo uma ocorrência (`registro.recorrencia_origem_id`).
 */
class RecorrenciaService
{
    public const PERIODOS = [
        'semanal'    => ['dias' => 7, 'rotulo' => 'Toda semana'],
        'quinzenal'  => ['dias' => 14, 'rotulo' => 'A cada 15 dias'],
        'mensal'     => ['meses' => 1, 'rotulo' => 'Todo mês'],
        'trimestral' => ['meses' => 3, 'rotulo' => 'A cada 3 meses'],
        'anual'      => ['meses' => 12, 'rotulo' => 'Todo ano'],
    ];

    /**
     * Próxima data do período. Meses mantêm o dia quando ele existe e, senão,
     * usam o último dia do mês (31/01 + 1 mês = 28 ou 29/02, não 03/03).
     */
    public static function proximaData(string $data, string $periodo): string
    {
        $regra = self::PERIODOS[$periodo] ?? null;
        $d     = new \DateTimeImmutable($data);
        if ($regra === null) {
            return $data;
        }
        if (isset($regra['dias'])) {
            return $d->modify("+{$regra['dias']} days")->format('Y-m-d');
        }

        $ano  = (int) $d->format('Y');
        $mes  = (int) $d->format('n') + $regra['meses'];
        $ano += intdiv($mes - 1, 12);
        $mes  = ($mes - 1) % 12 + 1;
        $dia  = min((int) $d->format('j'), (int) (new \DateTimeImmutable("{$ano}-{$mes}-01"))->format('t'));

        return sprintf('%04d-%02d-%02d', $ano, $mes, $dia);
    }

    /**
     * Monta os dados da próxima ocorrência, ou `null` se o item não deve
     * gerar uma agora (sem repetição, sem prazo, ou não acabou de ser concluído).
     *
     * @param array<int, array{id: string, tipo: string, opcoes?: mixed}> $campos
     */
    public static function proximaOcorrencia(array $campos, array $anteriores, array $novos): ?array
    {
        $porTipo = [];
        foreach ($campos as $c) {
            $porTipo[$c['tipo']][] = $c;
        }

        $repeticao = null;
        foreach ($porTipo['recorrencia'] ?? [] as $c) {
            if (isset(self::PERIODOS[$novos[$c['id']] ?? ''])) {
                $repeticao = $novos[$c['id']];
                break;
            }
        }
        $prazo = $porTipo['prazo'][0] ?? null;
        if ($repeticao === null || $prazo === null || empty($novos[$prazo['id']])) {
            return null;
        }

        // Só no momento em que vira concluído (não a cada edição de um item já concluído).
        $listas         = $porTipo['selecao'] ?? [];
        $concluidoAgora = false;
        $statusId       = null;
        foreach ($listas as $c) {
            $depois = PrazoService::opcaoDeConclusao(isset($novos[$c['id']]) ? (string) $novos[$c['id']] : null);
            $antes  = PrazoService::opcaoDeConclusao(isset($anteriores[$c['id']]) ? (string) $anteriores[$c['id']] : null);
            if ($depois && ! $antes) {
                $concluidoAgora = true;
                $statusId       = $c['id'];
                break;
            }
        }
        if (! $concluidoAgora) {
            return null;
        }

        $proximo                  = $novos;
        $proximo[$prazo['id']]    = self::proximaData((string) $novos[$prazo['id']], $repeticao);
        $opcoes                   = self::opcoes($campos, $statusId);
        $inicial                  = null;
        foreach ($opcoes as $op) {
            if (! PrazoService::opcaoDeConclusao($op)) {
                $inicial = $op;
                break;
            }
        }
        if ($inicial === null) {
            unset($proximo[$statusId]);
        } else {
            $proximo[$statusId] = $inicial;
        }

        return $proximo;
    }

    private static function opcoes(array $campos, string $campoId): array
    {
        foreach ($campos as $c) {
            if ($c['id'] === $campoId) {
                $op = $c['opcoes'] ?? [];

                return is_string($op) ? (json_decode($op, true) ?: []) : (array) $op;
            }
        }

        return [];
    }

    /**
     * Cria a próxima ocorrência depois de salvar um item, se for o caso.
     * Nunca derruba o salvamento: um erro aqui vai para o log.
     *
     * @return ?string id da ocorrência criada
     */
    public function depoisDeSalvar(string $registroId, string $moduloId, array $campos, array $anteriores, array $novos, ?string $usuarioId): ?string
    {
        $proximo = self::proximaOcorrencia($campos, $anteriores, $novos);
        if ($proximo === null) {
            return null;
        }

        try {
            $db = db_connect();
            $jaGerou = $db->table('registro')->where('recorrencia_origem_id', $registroId)->countAllResults() > 0;
            if ($jaGerou) {
                return null;
            }

            helper('uuid');
            $id = generate_uuid_v7();
            $db->table('registro')->insert([
                'id'                    => $id,
                'modulo_id'             => $moduloId,
                'dados'                 => json_encode($proximo, JSON_UNESCAPED_UNICODE),
                'criado_por'            => $usuarioId,
                'recorrencia_origem_id' => $registroId,
                'criado_em'             => gmdate('Y-m-d H:i:s'),
                'atualizado_em'         => gmdate('Y-m-d H:i:s'),
            ]);

            // Mesma porta de entrada de um item criado à mão: automações e histórico.
            Events::trigger('registro_criado', [
                'modulo_id'      => $moduloId,
                'registro_id'    => $id,
                'dados_registro' => $proximo,
            ]);

            return $id;
        } catch (\Throwable $e) {
            log_message('error', 'Não foi possível criar a próxima ocorrência de {id}: {erro}', ['id' => $registroId, 'erro' => $e->getMessage()]);

            return null;
        }
    }
}
