<?php

namespace App\Services\Recrutamento;

/**
 * Montagem do kanban de candidatos (cálculo puro, testável sem banco) e o
 * formato em que um candidato sai para a tela.
 */
final class Kanban
{
    /**
     * Kanban de uma vaga: uma coluna por etapa, na ordem recebida. Candidato
     * numa etapa que não está na lista fica de fora.
     *
     * @param list<array> $fases      etapas da vaga, ordenadas
     * @param list<array> $candidatos candidatos da vaga, na ordem de exibição
     *
     * @return array<string, array{fase: string, total: int, candidatos: list<array>}> por id da etapa
     */
    public static function porEtapa(array $fases, array $candidatos): array
    {
        $kanban = [];
        foreach ($fases as $fase) {
            $kanban[$fase['id']] = ['fase' => $fase['nome'], 'total' => 0, 'candidatos' => []];
        }

        foreach ($candidatos as $candidato) {
            $fase = $candidato['fase_atual_id'];
            if (isset($kanban[$fase])) {
                $kanban[$fase]['candidatos'][] = self::apresentar($candidato);
                $kanban[$fase]['total']++;
            }
        }

        return $kanban;
    }

    /**
     * Kanban de várias vagas: etapas de mesmo nome (sem diferença de
     * maiúsculas e espaços nas pontas) viram uma coluna só; candidato numa
     * etapa desconhecida vai para "Outros". Cada candidato leva o nome da vaga.
     *
     * @param list<array>           $fases         etapas das vagas, ordenadas
     * @param list<array>           $candidatos    candidatos das vagas, na ordem de exibição
     * @param array<string, string> $nomeVagaPorId
     *
     * @return array<string, array{fase: string, total: int, candidatos: list<array>}> por nome da etapa, em minúsculas
     */
    public static function porNomeDaEtapa(array $fases, array $candidatos, array $nomeVagaPorId): array
    {
        $nomeFasePorId = array_column($fases, 'nome', 'id');

        $kanban = [];
        foreach ($fases as $fase) {
            $chave = strtolower(trim($fase['nome']));
            if (! isset($kanban[$chave])) {
                $kanban[$chave] = ['fase' => $fase['nome'], 'total' => 0, 'candidatos' => []];
            }
        }

        foreach ($candidatos as $candidato) {
            $nomeFase = $nomeFasePorId[$candidato['fase_atual_id']] ?? null;
            $chave    = $nomeFase ? strtolower(trim($nomeFase)) : 'outros';

            if (! isset($kanban[$chave])) {
                $kanban[$chave] = ['fase' => $nomeFase ?? 'Outros', 'total' => 0, 'candidatos' => []];
            }

            $candidato['vaga'] = $nomeVagaPorId[$candidato['modulo_id']] ?? null;
            $kanban[$chave]['candidatos'][] = self::apresentar($candidato);
            $kanban[$chave]['total']++;
        }

        return $kanban;
    }

    /**
     * O que sai para a tela: a chave do storage nunca, só se há currículo e o
     * nome dele. "Cargo desejado" saiu do produto; a coluna antiga fica no banco.
     */
    public static function apresentar(array $candidato): array
    {
        $candidato['tem_curriculo'] = ! empty($candidato['curriculo_chave']);
        unset($candidato['curriculo_chave'], $candidato['curriculo_mime'], $candidato['cargo_desejado']);

        return $candidato;
    }
}
