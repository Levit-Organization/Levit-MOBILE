<?php

namespace App\Services\Financeiro;

/**
 * Validação dos campos FIXOS do lançamento (data, tipo, valor etc). Campos
 * extras são tratados à parte, via RegistroService. Só a conferência da
 * categoria consulta o banco, pela função recebida.
 */
final class ValidadorDeLancamento
{
    private const TIPOS_LANCAMENTO_VALIDOS = ['entrada', 'saida'];

    /**
     * Valida e monta as colunas do lançamento.
     *
     * @param bool $parcial quando true (update), campos ausentes em
     *        $dados são ignorados; quando false (create), os campos
     *        obrigatórios precisam estar presentes
     * @param \Closure(mixed): bool $categoriaDaEmpresa se a categoria informada existe e é da empresa
     *
     * @throws \DomainException
     */
    public static function validar(array $dados, bool $parcial, \Closure $categoriaDaEmpresa): array
    {
        $campos = [];

        if (array_key_exists('descricao', $dados)) {
            $descricao = trim((string) $dados['descricao']);

            if ($descricao === '') {
                throw new \DomainException('A descrição não pode ficar vazia.');
            }

            $campos['descricao'] = $descricao;
        } elseif (! $parcial) {
            throw new \DomainException('A descrição é obrigatória.');
        }

        if (array_key_exists('data', $dados)) {
            $data = \DateTime::createFromFormat('Y-m-d', (string) ($dados['data'] ?? ''));

            if (! $data || $data->format('Y-m-d') !== $dados['data']) {
                throw new \DomainException('A data precisa estar no formato AAAA-MM-DD.');
            }

            $campos['data_lancamento'] = $dados['data'];
        } elseif (! $parcial) {
            throw new \DomainException('A data é obrigatória.');
        }

        if (array_key_exists('tipo', $dados)) {
            if (! in_array($dados['tipo'], self::TIPOS_LANCAMENTO_VALIDOS, true)) {
                throw new \DomainException("Tipo de lançamento inválido: '{$dados['tipo']}'. Use 'entrada' ou 'saida'.");
            }

            $campos['tipo'] = $dados['tipo'];
        } elseif (! $parcial) {
            throw new \DomainException('O tipo do lançamento é obrigatório.');
        }

        if (array_key_exists('valor', $dados)) {
            if (! is_numeric($dados['valor']) || $dados['valor'] <= 0) {
                throw new \DomainException('O valor precisa ser um número maior que zero.');
            }

            $campos['valor'] = $dados['valor'] + 0;
        } elseif (! $parcial) {
            throw new \DomainException('O valor é obrigatório.');
        }

        if (array_key_exists('conciliado', $dados)) {
            $campos['conciliado'] = (bool) $dados['conciliado'];
        }

        if (array_key_exists('categoria_id', $dados)) {
            if ($dados['categoria_id'] !== null && ! $categoriaDaEmpresa($dados['categoria_id'])) {
                throw new \DomainException('Categoria inválida ou não pertence à empresa.');
            }

            $campos['categoria_id'] = $dados['categoria_id'];
        }

        return $campos;
    }
}
