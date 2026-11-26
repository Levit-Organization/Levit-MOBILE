<?php

namespace Tests\Support\Models;

/**
 * Model em memória para testes de services: não abre conexão, guarda os
 * métodos encadeados (where, join, orderBy...) e responde as consultas com o
 * que o teste deixou em `$respostas`.
 *
 * Use numa classe anônima que estende o model de verdade, para passar no tipo
 * da propriedade do service:
 *
 *   $fases = new class () extends FaseRecrutamentoModel { use ModeloFalso; };
 *   $fases->respostas['findAll'] = [[...], [...]];
 *
 * Cada resposta é uma lista consumida em ordem (uma por chamada) ou uma
 * Closure(array $consulta, array $argumentos) que recebe os encadeamentos da
 * consulta atual como [[metodo, argumentos], ...].
 */
trait ModeloFalso
{
    /** @var array<string, array|\Closure> */
    public array $respostas = [];

    /** @var list<array{0: string, 1: list<array{0: string, 1: array}>, 2: array}> consultas encerradas: [método final, encadeamentos, argumentos] */
    public array $consultas = [];

    /** @var list<array{0: string, 1: array}> */
    private array $encadeados = [];

    public function __construct()
    {
    }

    public function __call(string $name, array $params)
    {
        $this->encadeados[] = [$name, $params];

        return $this;
    }

    public function first()
    {
        return $this->responder('first', []);
    }

    public function findAll(?int $limit = null, int $offset = 0)
    {
        return $this->responder('findAll', []) ?? [];
    }

    public function find($id = null)
    {
        return $this->responder('find', [$id]);
    }

    public function insert($row = null, bool $returnID = true)
    {
        return $this->responder('insert', [$row]);
    }

    public function update($id = null, $row = null): bool
    {
        return $this->responder('update', [$id, $row]) ?? true;
    }

    public function delete($id = null, bool $purge = false)
    {
        return $this->responder('delete', [$id]) ?? true;
    }

    /** Encadeamentos (where, whereIn...) da n-ésima consulta encerrada com `$metodo`. */
    public function consulta(string $metodo, int $n = 0): array
    {
        $doMetodo = array_values(array_filter($this->consultas, static fn ($c) => $c[0] === $metodo));

        return $doMetodo[$n][1] ?? [];
    }

    private function responder(string $metodo, array $argumentos): mixed
    {
        $consulta           = $this->encadeados;
        $this->encadeados   = [];
        $this->consultas[]  = [$metodo, $consulta, $argumentos];
        $resposta           = $this->respostas[$metodo] ?? null;

        if ($resposta instanceof \Closure) {
            return $resposta($consulta, $argumentos);
        }

        return is_array($resposta) && $resposta !== [] ? array_shift($this->respostas[$metodo]) : null;
    }
}
