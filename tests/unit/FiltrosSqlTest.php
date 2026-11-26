<?php

use App\Services\Pesquisa\Filtros;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Retrato do SQL que a pesquisa avançada gera hoje, caso a caso (condição e
 * parâmetros exatos), e do texto que descreve os filtros. Serve para mexer na
 * organização do código sem mudar uma vírgula do que vai para o banco.
 *
 * @internal
 */
final class FiltrosSqlTest extends CIUnitTestCase
{
    private const P    = '00000000-0000-7000-8000-00000000000a';
    private const C    = '00000000-0000-7000-8000-00000000000b';
    private const X    = '00000000-0000-7000-8000-00000000000c';
    private const ITEM = '0192f0a0-0000-7000-8000-000000000001';

    private function campos(): array
    {
        return [
            'nome'    => ['id' => 'nome', 'modulo_id' => self::P, 'nome' => 'Pedido', 'tipo' => 'texto', 'modulo_vinculado_id' => null],
            'valor'   => ['id' => 'valor', 'modulo_id' => self::P, 'nome' => 'Valor', 'tipo' => 'numero', 'modulo_vinculado_id' => null],
            'entrega' => ['id' => 'entrega', 'modulo_id' => self::P, 'nome' => 'Entrega', 'tipo' => 'data', 'modulo_vinculado_id' => null],
            'prazo'   => ['id' => 'prazo', 'modulo_id' => self::P, 'nome' => 'Prazo', 'tipo' => 'prazo', 'modulo_vinculado_id' => null],
            'status'  => ['id' => 'status', 'modulo_id' => self::P, 'nome' => 'Status', 'tipo' => 'selecao', 'modulo_vinculado_id' => null],
            'dono'    => ['id' => 'dono', 'modulo_id' => self::P, 'nome' => 'Dono', 'tipo' => 'responsavel', 'modulo_vinculado_id' => null],
            'repete'  => ['id' => 'repete', 'modulo_id' => self::P, 'nome' => 'Repete', 'tipo' => 'recorrencia', 'modulo_vinculado_id' => null],
            'cliente' => ['id' => 'cliente', 'modulo_id' => self::P, 'nome' => 'Cliente', 'tipo' => 'vinculo', 'modulo_vinculado_id' => self::C],
            'secreto' => ['id' => 'secreto', 'modulo_id' => self::P, 'nome' => 'Secreto', 'tipo' => 'vinculo', 'modulo_vinculado_id' => self::X],
            'cidade'  => ['id' => 'cidade', 'modulo_id' => self::C, 'nome' => 'Cidade', 'tipo' => 'texto', 'modulo_vinculado_id' => null],
        ];
    }

    /** @return array<string, array{0: array, 1: string, 2: string}> filtro, tabela e apelido */
    private function casos(): array
    {
        $r = static fn (string $campo, string $operador, ?string $valor = null, array $extra = []) => ['logica' => 'e', 'regras' => [['campo' => $campo, 'operador' => $operador, 'valor' => $valor] + $extra]];

        return [
            'texto igual'            => [$r('nome', 'igual', ' Ana '), self::P, 'r'],
            'texto diferente'        => [$r('nome', 'diferente', 'Ana'), self::P, 'r'],
            'texto nao_contem'       => [$r('nome', 'nao_contem', 'a\\b'), self::P, 'r'],
            'texto preenchido'       => [$r('nome', 'preenchido'), self::P, 'x'],
            'numero igual'           => [$r('valor', 'igual', '10.5'), self::P, 'r'],
            'numero diferente'       => [$r('valor', 'diferente', '-3'), self::P, 'r'],
            'numero menor'           => [$r('valor', 'menor', '0'), self::P, 'r'],
            'numero entre'           => [$r('valor', 'entre', '1', ['valor_ate' => '9']), self::P, 'r'],
            'numero vazio'           => [$r('valor', 'vazio'), self::P, 'r'],
            'data igual'             => [$r('entrega', 'igual', '2026-02-28'), self::P, 'r'],
            'data vazio'             => [$r('entrega', 'preenchido'), self::P, 'r'],
            'momento entre'          => [$r('atualizado_em', 'entre', '2026-01-01', ['valor_ate' => '2026-01-31']), self::P, 'r'],
            'selecao igual'          => [$r('status', 'igual', 'Aberto'), self::P, 'r'],
            'vinculo diferente'      => [$r('cliente', 'diferente', self::ITEM), self::P, 'r'],
            'vinculo vazio'          => [$r('cliente', 'vazio'), self::P, 'r'],
            'vinculo preenchido'     => [$r('cliente', 'preenchido'), self::P, 'r'],
            'ida sem coluna'         => [['logica' => 'e', 'regras' => [['operador' => 'preenchido', 'via' => [['campo' => 'cliente']]]]], self::P, 'r'],
            'volta nenhum numero'    => [$r('valor', 'entre', '5', ['valor_ate' => '1', 'via' => [['campo' => 'cliente', 'sentido' => 'volta']], 'quantificador' => 'nenhum']), self::C, 'r'],
            'ida e volta'            => [$r('valor', 'maior', '100', ['via' => [['campo' => 'cliente', 'sentido' => 'ida'], ['campo' => 'cliente', 'sentido' => 'volta']]]), self::P, 'r'],
            'grupos e ligacoes'      => [['logica' => 'ou', 'regras' => [
                ['campo' => 'nome', 'operador' => 'contem', 'valor' => 'pão'],
                ['logica' => 'e', 'regras' => [
                    ['campo' => 'cidade', 'operador' => 'vazio', 'via' => [['campo' => 'cliente']]],
                    ['logica' => 'ou', 'regras' => []],
                    ['campo' => 'criado_em', 'operador' => 'maior', 'valor' => '2026-05-01'],
                ]],
                ['logica' => 'e', 'regras' => [['campo' => 'status', 'operador' => 'vazio']]],
            ]], self::P, 'reg'],
            'sem logica'             => [['regras' => [['campo' => 'nome', 'operador' => 'vazio']]], self::P, 'r'],
            'vazio sem regras'       => [[], self::P, 'r'],
            'niveis demais'          => [['regras' => [['regras' => [['regras' => [['regras' => []]]]]]]], self::P, 'r'],
            'tres niveis'            => [['regras' => [['regras' => [['regras' => [['campo' => 'nome', 'operador' => 'vazio']]]]]]], self::P, 'r'],
            'regras nao lista'       => [['regras' => 'x'], self::P, 'r'],
            'regra nao array'        => [['regras' => ['x']], self::P, 'r'],
            'via nao lista'          => [$r('nome', 'vazio', null, ['via' => ['a' => ['campo' => 'cliente']]]), self::P, 'r'],
            'saltos demais'          => [$r('nome', 'vazio', null, ['via' => array_fill(0, 4, ['campo' => 'cliente'])]), self::P, 'r'],
            'quantificador ruim'     => [$r('cidade', 'vazio', null, ['via' => [['campo' => 'cliente']], 'quantificador' => 'todos']), self::P, 'r'],
            'salto nao array'        => [$r('cidade', 'vazio', null, ['via' => ['cliente']]), self::P, 'r'],
            'salto coluna comum'     => [$r('cidade', 'vazio', null, ['via' => [['campo' => 'nome']]]), self::P, 'r'],
            'salto coluna sumiu'     => [$r('cidade', 'vazio', null, ['via' => [['campo' => 'zzz']]]), self::P, 'r'],
            'sem coluna operador'    => [['regras' => [['operador' => 'igual', 'via' => [['campo' => 'cliente']]]]], self::P, 'r'],
            'valor longo'            => [$r('nome', 'igual', str_repeat('a', 501)), self::P, 'r'],
            'valor nao escalar'      => [['regras' => [['campo' => 'nome', 'operador' => 'igual', 'valor' => ['a']]]], self::P, 'r'],
            'entre sem ate'          => [$r('valor', 'entre', '1'), self::P, 'r'],
            'data invalida ate'      => [$r('entrega', 'entre', '2026-01-01', ['valor_ate' => '2026-13-01']), self::P, 'r'],
            'vinculo sem item'       => [$r('cliente', 'igual', ''), self::P, 'r'],
            'vinculo maiusculo'      => [$r('cliente', 'igual', strtoupper(self::ITEM)), self::P, 'r'],
            'sem acesso a ligada'    => [$r('cidade', 'vazio', null, ['via' => [['campo' => 'secreto']]]), self::P, 'r'],
            'campo nao string'       => [['regras' => [['campo' => 5, 'operador' => 'vazio']]], self::P, 'r'],
            'operador desconhecido'  => [$r('nome', 'parecido', 'x'), self::P, 'r'],
            'momento contem'         => [$r('criado_em', 'contem', 'x'), self::P, 'r'],
        ];
    }

    /** @return array<string, array{0: string, 1: array<string, string>|string}> condição e parâmetros, ou exceção e mensagem */
    private function esperado(): array
    {
        return [
            'texto igual' => ['lower((r.dados->>:f1:)) = lower(:f2:)', ['f1' => 'nome', 'f2' => 'Ana']],
            'texto diferente' => ['((r.dados->>:f1:) IS NULL OR lower((r.dados->>:f1:)) <> lower(:f2:))', ['f1' => 'nome', 'f2' => 'Ana']],
            'texto nao_contem' => ['((r.dados->>:f1:) IS NULL OR (r.dados->>:f1:) NOT ILIKE :f2:)', ['f1' => 'nome', 'f2' => '%a\\\\b%']],
            'texto preenchido' => ['NOT ((x.dados->>:f1:) IS NULL OR btrim((x.dados->>:f1:)) = \'\')', ['f1' => 'nome']],
            'numero igual' => ['(CASE WHEN jsonb_typeof(r.dados->:f1:) = \'number\' THEN (r.dados->>:f1:)::numeric END) = :f2:::numeric', ['f1' => 'valor', 'f2' => '10.5']],
            'numero diferente' => ['((CASE WHEN jsonb_typeof(r.dados->:f1:) = \'number\' THEN (r.dados->>:f1:)::numeric END) IS NULL OR (CASE WHEN jsonb_typeof(r.dados->:f1:) = \'number\' THEN (r.dados->>:f1:)::numeric END) <> :f2:::numeric)', ['f1' => 'valor', 'f2' => '-3']],
            'numero menor' => ['(CASE WHEN jsonb_typeof(r.dados->:f1:) = \'number\' THEN (r.dados->>:f1:)::numeric END) < :f2:::numeric', ['f1' => 'valor', 'f2' => '0']],
            'numero entre' => ['(CASE WHEN jsonb_typeof(r.dados->:f1:) = \'number\' THEN (r.dados->>:f1:)::numeric END) BETWEEN SYMMETRIC :f2:::numeric AND :f3:::numeric', ['f1' => 'valor', 'f2' => '1', 'f3' => '9']],
            'numero vazio' => ['((CASE WHEN jsonb_typeof(r.dados->:f1:) = \'number\' THEN (r.dados->>:f1:)::numeric END) IS NULL)', ['f1' => 'valor']],
            'data igual' => ['(r.dados->>:f1:) = :f2:', ['f1' => 'entrega', 'f2' => '2026-02-28']],
            'data vazio' => ['NOT ((r.dados->>:f1:) IS NULL)', ['f1' => 'entrega']],
            'momento entre' => ['r.atualizado_em::date BETWEEN SYMMETRIC :f1:::date AND :f2:::date', ['f1' => '2026-01-01', 'f2' => '2026-01-31']],
            'selecao igual' => ['(r.dados->>:f1:) = :f2:', ['f1' => 'status', 'f2' => 'Aberto']],
            'vinculo diferente' => ['NOT (r.dados @> jsonb_build_object(:f1:::text, :f2:::text) OR r.dados @> jsonb_build_object(:f1:::text, jsonb_build_array(:f2:::text)))', ['f1' => 'cliente', 'f2' => '0192f0a0-0000-7000-8000-000000000001']],
            'vinculo vazio' => ['(COALESCE(r.dados->:f1:, \'[]\'::jsonb) = \'[]\'::jsonb)', ['f1' => 'cliente']],
            'vinculo preenchido' => ['NOT (COALESCE(r.dados->:f1:, \'[]\'::jsonb) = \'[]\'::jsonb)', ['f1' => 'cliente']],
            'ida sem coluna' => ['EXISTS (SELECT 1 FROM registro v1 WHERE v1.modulo_id = :f2: AND v1.id IN (SELECT e::uuid FROM jsonb_array_elements_text(CASE jsonb_typeof(r.dados->:f1:) WHEN \'array\' THEN r.dados->:f1: WHEN \'string\' THEN jsonb_build_array(r.dados->:f1:) ELSE \'[]\'::jsonb END) AS e WHERE e ~* \'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$\'))', ['f1' => 'cliente', 'f2' => '00000000-0000-7000-8000-00000000000b']],
            'volta nenhum numero' => ['NOT EXISTS (SELECT 1 FROM registro v1 WHERE v1.modulo_id = :f2: AND (v1.dados @> jsonb_build_object(:f1:::text, r.id::text) OR v1.dados @> jsonb_build_object(:f1:::text, jsonb_build_array(r.id::text))) AND (CASE WHEN jsonb_typeof(v1.dados->:f3:) = \'number\' THEN (v1.dados->>:f3:)::numeric END) BETWEEN SYMMETRIC :f4:::numeric AND :f5:::numeric)', ['f1' => 'cliente', 'f2' => '00000000-0000-7000-8000-00000000000a', 'f3' => 'valor', 'f4' => '5', 'f5' => '1']],
            'ida e volta' => ['EXISTS (SELECT 1 FROM registro v1 WHERE v1.modulo_id = :f2: AND v1.id IN (SELECT e::uuid FROM jsonb_array_elements_text(CASE jsonb_typeof(r.dados->:f1:) WHEN \'array\' THEN r.dados->:f1: WHEN \'string\' THEN jsonb_build_array(r.dados->:f1:) ELSE \'[]\'::jsonb END) AS e WHERE e ~* \'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$\') AND EXISTS (SELECT 1 FROM registro v2 WHERE v2.modulo_id = :f4: AND (v2.dados @> jsonb_build_object(:f3:::text, v1.id::text) OR v2.dados @> jsonb_build_object(:f3:::text, jsonb_build_array(v1.id::text))) AND (CASE WHEN jsonb_typeof(v2.dados->:f5:) = \'number\' THEN (v2.dados->>:f5:)::numeric END) > :f6:::numeric))', ['f1' => 'cliente', 'f2' => '00000000-0000-7000-8000-00000000000b', 'f3' => 'cliente', 'f4' => '00000000-0000-7000-8000-00000000000a', 'f5' => 'valor', 'f6' => '100']],
            'grupos e ligacoes' => ['((reg.dados->>:f1:) ILIKE :f2: OR (EXISTS (SELECT 1 FROM registro v1 WHERE v1.modulo_id = :f4: AND v1.id IN (SELECT e::uuid FROM jsonb_array_elements_text(CASE jsonb_typeof(reg.dados->:f3:) WHEN \'array\' THEN reg.dados->:f3: WHEN \'string\' THEN jsonb_build_array(reg.dados->:f3:) ELSE \'[]\'::jsonb END) AS e WHERE e ~* \'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$\') AND ((v1.dados->>:f5:) IS NULL OR btrim((v1.dados->>:f5:)) = \'\')) AND reg.criado_em::date > :f6:::date) OR ((reg.dados->>:f7:) IS NULL))', ['f1' => 'nome', 'f2' => '%pão%', 'f3' => 'cliente', 'f4' => '00000000-0000-7000-8000-00000000000b', 'f5' => 'cidade', 'f6' => '2026-05-01', 'f7' => 'status']],
            'sem logica' => ['((r.dados->>:f1:) IS NULL OR btrim((r.dados->>:f1:)) = \'\')', ['f1' => 'nome']],
            'vazio sem regras' => ['TRUE', []],
            'niveis demais' => [\DomainException::class, 'O filtro tem grupos dentro de grupos demais.'],
            'tres niveis' => ['((r.dados->>:f1:) IS NULL OR btrim((r.dados->>:f1:)) = \'\')', ['f1' => 'nome']],
            'regras nao lista' => [\DomainException::class, 'Filtro inválido. Refaça o filtro.'],
            'regra nao array' => [\DomainException::class, 'Filtro inválido. Refaça o filtro.'],
            'via nao lista' => [\DomainException::class, 'Filtro inválido. Refaça o filtro.'],
            'saltos demais' => [\DomainException::class, 'Um filtro passa por no máximo 3 ligações entre tabelas.'],
            'quantificador ruim' => [\DomainException::class, 'Filtro inválido. Refaça o filtro.'],
            'salto nao array' => [\DomainException::class, 'Filtro inválido. Refaça o filtro.'],
            'salto coluna comum' => [\DomainException::class, 'A coluna “Pedido” não liga a outra tabela.'],
            'salto coluna sumiu' => [\DomainException::class, 'Uma das colunas do filtro não existe mais. Refaça o filtro.'],
            'sem coluna operador' => [\DomainException::class, 'Escolha a coluna da tabela ligada.'],
            'valor longo' => [\DomainException::class, 'O valor do filtro em “Pedido” é longo demais.'],
            'valor nao escalar' => [\DomainException::class, 'Informe o valor do filtro em “Pedido”.'],
            'entre sem ate' => [\DomainException::class, 'Informe o valor do filtro em “Valor”.'],
            'data invalida ate' => [\DomainException::class, 'O filtro em “Entrega” precisa de uma data (AAAA-MM-DD).'],
            'vinculo sem item' => [\DomainException::class, 'Escolha o item da coluna “Cliente”.'],
            'vinculo maiusculo' => ['(r.dados @> jsonb_build_object(:f1:::text, :f2:::text) OR r.dados @> jsonb_build_object(:f1:::text, jsonb_build_array(:f2:::text)))', ['f1' => 'cliente', 'f2' => '0192F0A0-0000-7000-8000-000000000001']],
            'sem acesso a ligada' => [\App\Exceptions\AcessoNegadoException::class, 'Você não tem acesso à tabela ligada pela coluna “Secreto”.'],
            'campo nao string' => [\DomainException::class, 'Uma das colunas do filtro não existe mais. Refaça o filtro.'],
            'operador desconhecido' => [\DomainException::class, 'Essa comparação não vale para a coluna “Pedido”.'],
            'momento contem' => [\DomainException::class, 'Essa comparação não vale para a coluna “Criado em”.'],
        ];
    }

    public function testSqlDeCadaCasoNaoMuda(): void
    {
        // A mesma instância para todos: cada chamada recomeça os parâmetros, mesmo depois de um erro.
        $filtros  = new Filtros($this->campos(), static fn (string $id): bool => $id !== self::X);
        $esperado = $this->esperado();

        foreach ($this->casos() as $nome => [$filtro, $modulo, $apelido]) {
            [$a, $b] = $esperado[$nome];

            try {
                $obtido = $filtros->paraSql($filtro, $modulo, $apelido);
                $this->assertSame([$a, $b], $obtido, $nome);
            } catch (\DomainException|\App\Exceptions\AcessoNegadoException $e) {
                $this->assertSame([$a, $b], [$e::class, $e->getMessage()], $nome);
            }
        }
        $this->assertSame(array_keys($esperado), array_keys($this->casos()));
    }

    public function testValorAte500CaracteresELimiteExatoDeRegras(): void
    {
        $filtros = new Filtros($this->campos(), static fn () => true);
        $longo   = str_repeat('é', 500);

        $this->assertSame(
            ['lower((r.dados->>:f1:)) = lower(:f2:)', ['f1' => 'nome', 'f2' => $longo]],
            $filtros->paraSql(['regras' => [['campo' => 'nome', 'operador' => 'igual', 'valor' => $longo]]], self::P)
        );

        [$sql, $binds] = $filtros->paraSql(['regras' => array_fill(0, Filtros::MAX_REGRAS, ['campo' => 'nome', 'operador' => 'vazio'])], self::P);
        $partes        = [];
        $esperados     = [];
        for ($i = 1; $i <= Filtros::MAX_REGRAS; $i++) {
            $partes[]           = "((r.dados->>:f{$i}:) IS NULL OR btrim((r.dados->>:f{$i}:)) = '')";
            $esperados["f{$i}"] = 'nome';
        }
        $this->assertSame('(' . implode(' AND ', $partes) . ')', $sql);
        $this->assertSame($esperados, $binds);
    }

    public function testDescricaoDeCadaTipo(): void
    {
        $filtros = new Filtros($this->campos(), static fn () => true, [self::C => 'Clientes']);
        $nome    = static fn (array $campo, string $valor) => "[{$campo['tipo']}:{$valor}]";

        $this->assertSame([
            'Valor é igual a 1.234.567,89',
            'Valor maior que 10',
            'Valor menor que abc',
            'Valor é diferente de -0,5',
            'Entrega é em 05/01/2026',
            'Prazo não é em 2026-13-40',
            'Prazo entre 01/01/2026 e 31/01/2026',
            'Atualizado em está vazio',
            'Dono é igual a [responsavel:u1]',
            'Repete é igual a Todo mês',
            'Repete é diferente de nunca',
            'Cliente não é [vinculo:' . self::ITEM . ']',
            'Cliente está preenchido',
            'Pedido não contém “x”',
            'Pedido desconhecido “y”',
            'Coluna removida é igual a “z”',
            'Cliente › Cidade está preenchido',
            'Cliente tem algum item',
            'Itens ligados não tem nenhum item',
            'Nenhum de Cliente › Cidade é igual a “Recife”',
            '(Pedido está vazio ou (Pedido contém “a” e Pedido contém “b”))',
            '()',
        ], $filtros->descrever(['regras' => [
            ['campo' => 'valor', 'operador' => 'igual', 'valor' => '1234567.891'],
            ['campo' => 'valor', 'operador' => 'maior', 'valor' => '10.00'],
            ['campo' => 'valor', 'operador' => 'menor', 'valor' => 'abc'],
            ['campo' => 'valor', 'operador' => 'diferente', 'valor' => '-0.5'],
            ['campo' => 'entrega', 'operador' => 'igual', 'valor' => ' 2026-01-05 '],
            ['campo' => 'prazo', 'operador' => 'diferente', 'valor' => '2026-13-40'],
            ['campo' => 'prazo', 'operador' => 'entre', 'valor' => '2026-01-01', 'valor_ate' => '2026-01-31'],
            ['campo' => 'atualizado_em', 'operador' => 'vazio'],
            ['campo' => 'dono', 'operador' => 'igual', 'valor' => 'u1'],
            ['campo' => 'repete', 'operador' => 'igual', 'valor' => 'mensal'],
            ['campo' => 'repete', 'operador' => 'diferente', 'valor' => 'nunca'],
            ['campo' => 'cliente', 'operador' => 'diferente', 'valor' => self::ITEM],
            ['campo' => 'cliente', 'operador' => 'preenchido'],
            ['campo' => 'nome', 'operador' => 'nao_contem', 'valor' => 'x'],
            ['campo' => 'nome', 'operador' => 'desconhecido', 'valor' => 'y'],
            ['campo' => 'sumiu', 'operador' => 'igual', 'valor' => 'z'],
            ['campo' => 'cidade', 'operador' => 'preenchido', 'via' => [['campo' => 'sumiu'], ['campo' => 'cliente']]],
            ['operador' => 'preenchido', 'via' => [['campo' => 'cliente']]],
            ['operador' => 'vazio', 'via' => [['campo' => 'cliente', 'sentido' => 'volta']]],
            ['campo' => 'cidade', 'operador' => 'igual', 'valor' => 'Recife', 'via' => [['campo' => 'cliente']], 'quantificador' => 'nenhum'],
            'não é regra',
            ['logica' => 'ou', 'regras' => [
                ['campo' => 'nome', 'operador' => 'vazio'],
                ['logica' => 'e', 'regras' => [['campo' => 'nome', 'operador' => 'contem', 'valor' => 'a'], ['campo' => 'nome', 'operador' => 'contem', 'valor' => 'b']]],
            ]],
            ['logica' => 'x', 'regras' => []],
        ]], $nome));

        $this->assertSame([], $filtros->descrever([], $nome));
    }

    public function testUuidValido(): void
    {
        $this->assertTrue(Filtros::uuidValido(self::ITEM));
        $this->assertTrue(Filtros::uuidValido(strtoupper(self::ITEM)));
        $this->assertFalse(Filtros::uuidValido(self::ITEM . ' '));
        $this->assertFalse(Filtros::uuidValido('0192f0a0-0000-7000-8000-00000000000g'));
        $this->assertFalse(Filtros::uuidValido(''));
    }
}
