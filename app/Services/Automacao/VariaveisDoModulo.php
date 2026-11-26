<?php

namespace App\Services\Automacao;

use App\Exceptions\FalhaPermanenteException;
use App\Models\CampoModuloModel;
use App\Models\ModuloModel;
use App\Services\RegistroService;

/**
 * O que as ações de uma automação precisam saber da tabela: a própria
 * tabela, as colunas (nome, tipo e tabela ligada), as pessoas das colunas
 * "Responsável" e a troca de {{variáveis}} pelos valores do item. Tudo fica
 * em memória durante a execução, para não repetir consultas a cada ação.
 */
class VariaveisDoModulo
{
    /** @var array<string, array<string, string>> id do campo => nome, por módulo */
    private array $campos = [];

    /** @var array<string, array{id: string, empresa_id: string, tipo: string, nome: string}> */
    private array $modulos = [];

    /** @var array<string, array<string, string>> id do campo => tipo, por módulo */
    private array $tipos = [];

    /** @var array<string, array<string, ?string>> id do campo => tabela ligada, por módulo */
    private array $ligadas = [];

    /** @var array<string, ?array{id: string, nome: string, email: string}> */
    private array $pessoas = [];

    /**
     * @param \Closure(): RegistroService $registros só é chamado quando uma
     *        variável de ligação precisa dos títulos dos itens ligados
     */
    public function __construct(private \Closure $registros)
    {
    }

    /** Substitui {{Nome do campo}} pelo valor (sem diferenciar maiúsculas). */
    public function substituir(string $texto, string $moduloId, array $dados): string
    {
        if (! str_contains($texto, '{{')) {
            return $texto;
        }

        $mapa      = $this->mapaDeVariaveis($moduloId);
        $ehDeDados = $this->modulo($moduloId)['tipo'] !== 'recrutamento';

        return preg_replace_callback('/\{\{(.*?)\}\}/', function (array $m) use ($mapa, $dados, $moduloId, $ehDeDados) {
            $chave = $mapa[mb_strtolower(trim($m[1]))] ?? null;
            $valor = $chave !== null ? ($dados[$chave] ?? '') : '';

            // {{Responsável}} mostra o nome da pessoa, não o identificador guardado.
            $tipo = $ehDeDados && $chave !== null && $valor !== '' ? $this->tipoDoCampo($moduloId, $chave) : null;
            if ($tipo === 'responsavel') {
                return $this->pessoa((string) $valor)['nome'] ?? '';
            }
            // {{Cliente}} (ligação) mostra o nome do item ligado, não o identificador.
            if ($tipo === 'vinculo') {
                $tabela = $this->ligadas[$moduloId][$chave] ?? null;

                return $tabela === null ? '' : implode(', ', array_filter(
                    ($this->registros)()->titulosDosItens($tabela, array_map('strval', (array) $valor)),
                    static fn ($titulo) => $titulo !== null && $titulo !== ''
                ));
            }
            // Datas e prazos no formato brasileiro (o banco guarda AAAA-MM-DD).
            if (in_array($tipo, ['data', 'prazo'], true) && preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', (string) $valor, $d)) {
                return "{$d[3]}/{$d[2]}/{$d[1]}";
            }

            return is_array($valor) ? implode(', ', $valor) : (string) $valor;
        }, $texto);
    }

    /** Em vagas, as variáveis são os campos do candidato; nos demais módulos, os campos do módulo. */
    private function mapaDeVariaveis(string $moduloId): array
    {
        if ($this->modulo($moduloId)['tipo'] === 'recrutamento') {
            return CamposCandidato::mapaDeVariaveis();
        }

        $mapa = [];
        foreach ($this->camposDoModulo($moduloId) as $id => $nome) {
            $mapa[mb_strtolower(trim($nome))] = $id;
        }

        return $mapa;
    }

    /** @return array<string, string> id => nome */
    public function camposDoModulo(string $moduloId): array
    {
        if (! isset($this->campos[$moduloId])) {
            $linhas                   = (new CampoModuloModel())->select('id, nome, tipo, modulo_vinculado_id')->where('modulo_id', $moduloId)->findAll();
            $this->campos[$moduloId]  = array_column($linhas, 'nome', 'id');
            $this->tipos[$moduloId]   = array_column($linhas, 'tipo', 'id');
            $this->ligadas[$moduloId] = array_column($linhas, 'modulo_vinculado_id', 'id');
        }

        return $this->campos[$moduloId];
    }

    public function tipoDoCampo(string $moduloId, string $campoId): ?string
    {
        $this->camposDoModulo($moduloId);

        return $this->tipos[$moduloId][$campoId] ?? null;
    }

    /** Pessoa da coluna "Responsável" do item (a primeira preenchida, ou a coluna indicada). */
    public function responsavelDoItem(string $moduloId, array $dados, ?string $campoId = null): ?array
    {
        $this->camposDoModulo($moduloId);
        foreach ($this->tipos[$moduloId] ?? [] as $id => $tipo) {
            if ($tipo === 'responsavel' && ($campoId === null || $campoId === $id) && ! empty($dados[$id])) {
                return $this->pessoa((string) $dados[$id]);
            }
        }

        return null;
    }

    private function pessoa(string $usuarioId): ?array
    {
        if (! array_key_exists($usuarioId, $this->pessoas)) {
            $this->pessoas[$usuarioId] = preg_match('/^[0-9a-f-]{36}$/i', $usuarioId)
                ? db_connect()->table('usuario')->select('id, nome, email')->where('id', $usuarioId)->get()->getRowArray()
                : null;
        }

        return $this->pessoas[$usuarioId];
    }

    /** @throws FalhaPermanenteException se a tabela não existir mais */
    public function modulo(string $moduloId): array
    {
        if (! isset($this->modulos[$moduloId])) {
            $modulo = $moduloId !== '' ? (new ModuloModel())->select('id, empresa_id, tipo, nome')->find($moduloId) : null;
            if (! $modulo) {
                throw new FalhaPermanenteException('A tabela usada por esta automação não existe mais.');
            }
            $this->modulos[$moduloId] = $modulo;
        }

        return $this->modulos[$moduloId];
    }
}
