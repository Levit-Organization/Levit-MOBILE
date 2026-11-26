<?php

namespace App\Services\Pesquisa;

use App\Services\RecorrenciaService;

/**
 * Os filtros em português, um por linha, para mostrar num relatório
 * ("Cliente › Cidade contém “Recife”"). Espera filtros já aceitos por
 * Filtros::paraSql; o que não reconhece aparece como veio.
 */
final class DescricaoDosFiltros
{
    /** Como cada operador aparece em texto (mesmos rótulos da tela de filtros). */
    private const ROTULOS = [
        'igual'      => 'é igual a',
        'diferente'  => 'é diferente de',
        'contem'     => 'contém',
        'nao_contem' => 'não contém',
        'maior'      => 'maior que',
        'menor'      => 'menor que',
        'entre'      => 'entre',
        'vazio'      => 'está vazio',
        'preenchido' => 'está preenchido',
    ];

    private const ROTULOS_DE_DATA = ['igual' => 'é em', 'diferente' => 'não é em', 'maior' => 'depois de', 'menor' => 'antes de'];

    /**
     * @param array<string, array> $campos colunas de todas as tabelas da empresa, por id
     * @param array<string, string> $tabelas nome das tabelas por id
     */
    public function __construct(private readonly array $campos, private readonly array $tabelas)
    {
    }

    /**
     * @param \Closure(array $campo, string $valor): string $nomeDoValor nome de uma pessoa ou de um item ligado
     *
     * @return list<string>
     */
    public function descrever(array $filtros, \Closure $nomeDoValor): array
    {
        $linhas = [];
        foreach ($filtros['regras'] ?? [] as $regra) {
            if (! is_array($regra)) {
                continue;
            }
            $linhas[] = array_key_exists('regras', $regra)
                ? '(' . implode(($regra['logica'] ?? 'e') === 'ou' ? ' ou ' : ' e ', $this->descrever($regra, $nomeDoValor)) . ')'
                : $this->frase($regra, $nomeDoValor);
        }

        return $linhas;
    }

    private function frase(array $regra, \Closure $nomeDoValor): string
    {
        $caminho = [];
        $volta   = false;
        foreach (is_array($regra['via'] ?? null) ? $regra['via'] : [] as $salto) {
            $ligacao = $this->campos[$salto['campo'] ?? ''] ?? null;
            if ($ligacao === null) {
                continue;
            }
            $deVolta = ($salto['sentido'] ?? 'ida') === 'volta';
            $volta   = $volta || $deVolta;
            // De volta, o caminho passa pela tabela de quem aponta (Pedidos); de ida, pela coluna (Cliente).
            $caminho[] = $deVolta ? ($this->tabelas[$ligacao['modulo_id']] ?? 'Itens ligados') : $ligacao['nome'];
        }

        $operador = (string) ($regra['operador'] ?? '');
        $campoId  = (string) ($regra['campo'] ?? '');

        if ($campoId === '') {
            return implode(' › ', $caminho) . ($operador === 'vazio' ? ' não tem nenhum item' : ' tem algum item');
        }

        $campo     = isset(Filtros::COLUNAS_DO_SISTEMA[$campoId])
            ? ['nome' => Filtros::COLUNAS_DO_SISTEMA[$campoId], 'tipo' => 'momento']
            : ($this->campos[$campoId] ?? ['nome' => 'Coluna removida', 'tipo' => 'texto']);
        $caminho[] = $campo['nome'];

        $prefixo = match (true) {
            ($regra['quantificador'] ?? 'algum') === 'nenhum' => 'Nenhum de ',
            $volta                                            => 'Algum de ',
            default                                           => '',
        };
        $rotulo = match (true) {
            in_array($campo['tipo'], ['data', 'prazo', 'momento'], true) => self::ROTULOS_DE_DATA[$operador] ?? null,
            $campo['tipo'] === 'vinculo'                                 => ['igual' => 'é', 'diferente' => 'não é'][$operador] ?? null,
            default                                                      => null,
        } ?? self::ROTULOS[$operador] ?? $operador;

        $frase = $prefixo . implode(' › ', $caminho) . ' ' . $rotulo;
        if (in_array($operador, ['vazio', 'preenchido'], true)) {
            return $frase;
        }

        $frase .= ' ' . $this->textoDoValor($campo, (string) ($regra['valor'] ?? ''), $nomeDoValor);

        return $operador === 'entre'
            ? $frase . ' e ' . $this->textoDoValor($campo, (string) ($regra['valor_ate'] ?? ''), $nomeDoValor)
            : $frase;
    }

    private function textoDoValor(array $campo, string $valor, \Closure $nomeDoValor): string
    {
        $valor = trim($valor);

        return match ($campo['tipo']) {
            'data', 'prazo', 'momento' => ValorDoFiltro::dataValida($valor) ? (new \DateTimeImmutable($valor))->format('d/m/Y') : $valor,
            'numero'                   => is_numeric($valor) ? rtrim(rtrim(number_format((float) $valor, 2, ',', '.'), '0'), ',') : $valor,
            'responsavel', 'vinculo'   => $nomeDoValor($campo, $valor),
            'recorrencia'              => RecorrenciaService::PERIODOS[$valor]['rotulo'] ?? $valor,
            default                    => "“{$valor}”",
        };
    }
}
