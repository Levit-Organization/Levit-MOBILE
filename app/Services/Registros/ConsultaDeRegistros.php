<?php

namespace App\Services\Registros;

use App\Exceptions\NaoEncontradoException;
use App\Models\CampoModuloModel;
use App\Models\ModuloModel;
use App\Models\RegistroModel;
use App\Services\AutorizacaoModuloService;
use App\Services\Pesquisa\Filtros;

/**
 * Leitura dos itens das tabelas: listagem com busca e pesquisa avançada,
 * tabelas de arquivos, títulos dos itens e o outro lado das ligações.
 */
class ConsultaDeRegistros
{
    /** Máximo de itens mostrados por ligação recebida (o total vem à parte). */
    public const MAX_LIGADOS_MOSTRADOS = 50;

    public function __construct(
        protected ModuloModel $moduloModel,
        protected CampoModuloModel $campoModuloModel,
        protected RegistroModel $registroModel,
        protected AutorizacaoModuloService $autorizacaoModuloService,
    ) {
    }

    /**
     * Itens da tabela, mais novos primeiro. `busca` procura nas colunas de texto;
     * `filtros` é a pesquisa avançada (ver Pesquisa\Filtros), que pode olhar os
     * dados das tabelas ligadas. As duas se somam.
     */
    public function listarRegistros(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, ?string $busca = null, ?array $filtros = null): array
    {
        $modulo = $this->moduloDaEmpresa($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        $busca = trim((string) $busca);

        if ($modulo['tipo'] === 'arquivo') {
            return $this->listarArquivos($moduloId, $busca);
        }

        $comFiltros = ! empty($filtros['regras']);

        if ($busca === '' && ! $comFiltros) {
            return $this->registroModel
                ->where('modulo_id', $moduloId)
                ->orderBy('criado_em', 'DESC')
                ->findAll();
        }

        $condicoes = ['r.modulo_id = :modulo:'];
        $binds     = ['modulo' => $moduloId];

        if ($busca !== '') {
            $camposTexto = $this->campoModuloModel
                ->where('modulo_id', $moduloId)
                ->where('tipo', 'texto')
                ->findAll();

            if (empty($camposTexto)) {
                return [];
            }

            $ou = [];
            foreach ($camposTexto as $campo) {
                $ou[] = "r.dados->>'{$campo['id']}' ILIKE :busca:";
            }
            $condicoes[]    = '(' . implode(' OR ', $ou) . ')';
            $binds['busca'] = '%' . $busca . '%';
        }

        if ($comFiltros) {
            [$sql, $parametros] = $this->pesquisa($empresaId, $cargoId, $acessoTotal)->paraSql($filtros, $moduloId, 'r');
            $condicoes[]        = $sql;
            $binds += $parametros;
        }

        $sql = 'SELECT r.* FROM registro r WHERE ' . implode(' AND ', $condicoes) . ' ORDER BY r.criado_em DESC';

        $registros = db_connect()->query($sql, $binds)->getResultArray();

        foreach ($registros as &$registro) {
            $registro['dados'] = json_decode($registro['dados'], true);
        }

        return $registros;
    }

    /**
     * Tabela de arquivos: cada item é um arquivo, então a lista traz o nome,
     * o tamanho, o tipo e quem enviou. A busca procura no nome do arquivo.
     * A chave no armazenamento nunca sai daqui.
     */
    private function listarArquivos(string $moduloId, string $busca): array
    {
        $sql = 'SELECT r.id, r.modulo_id, r.criado_por, r.criado_em, r.atualizado_em,
                       a.nome_original AS arquivo_nome, a.tamanho_bytes AS arquivo_tamanho,
                       a.tipo_mime AS arquivo_tipo, u.nome AS enviado_por_nome
                  FROM registro r
                  JOIN arquivo a ON a.registro_id = r.id
             LEFT JOIN usuario u ON u.id = r.criado_por
                 WHERE r.modulo_id = :modulo:';
        $binds = ['modulo' => $moduloId];

        if ($busca !== '') {
            $sql .= ' AND a.nome_original ILIKE :busca:';
            $binds['busca'] = '%' . $busca . '%';
        }

        $arquivos = db_connect()->query($sql . ' ORDER BY r.criado_em DESC', $binds)->getResultArray();

        foreach ($arquivos as &$arquivo) {
            $arquivo['arquivo_tamanho'] = (int) $arquivo['arquivo_tamanho'];
        }

        return $arquivos;
    }

    /** Pesquisa avançada com as colunas de todas as tabelas da empresa e o acesso de quem pesquisa. */
    private function pesquisa(string $empresaId, string $cargoId, bool $acessoTotal): Filtros
    {
        $campos = db_connect()->query(
            'SELECT c.id, c.modulo_id, c.nome, c.tipo, c.modulo_vinculado_id FROM campo_modulo c JOIN modulo m ON m.id = c.modulo_id WHERE m.empresa_id = ?',
            [$empresaId]
        )->getResultArray();

        return new Filtros(
            array_column($campos, null, 'id'),
            fn (string $moduloId): bool => $this->autorizacaoModuloService->filtrarModulosComNivel([$moduloId], $acessoTotal, $cargoId, 'visualizar') !== []
        );
    }

    /**
     * O outro lado das ligações: itens de outras tabelas (ou desta) que apontam
     * para este, agrupados pela coluna que faz a ligação. Ex.: num cliente, os
     * pedidos dele. Só entram tabelas que a pessoa vê, e ligações sem itens não.
     *
     * @return list<array{campo_id: string, campo_nome: string, modulo: array, total: int, itens: list<array{id: string, titulo: ?string}>}>
     */
    public function itensQueApontam(string $registroId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $this->buscarRegistro($registroId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        $ligacoes = $this->campoModuloModel
            ->select('campo_modulo.id, campo_modulo.nome, campo_modulo.modulo_id, modulo.nome AS modulo_nome, modulo.icone AS modulo_icone')
            ->join('modulo', 'modulo.id = campo_modulo.modulo_id')
            ->where('campo_modulo.modulo_vinculado_id', $moduloId)
            ->orderBy('modulo.nome', 'ASC')
            ->findAll();
        if ($ligacoes === []) {
            return [];
        }

        $visiveis = array_flip($this->autorizacaoModuloService->filtrarModulosComNivel(
            array_values(array_unique(array_column($ligacoes, 'modulo_id'))), $acessoTotal, $cargoId, 'visualizar'
        ));

        $grupos = [];
        foreach ($ligacoes as $ligacao) {
            if (! isset($visiveis[$ligacao['modulo_id']])) {
                continue;
            }

            $linhas = db_connect()->query(
                'SELECT id, COUNT(*) OVER () AS total FROM registro
                  WHERE modulo_id = :modulo:
                    AND (dados @> jsonb_build_object(:campo:::text, :item:::text) OR dados @> jsonb_build_object(:campo:::text, jsonb_build_array(:item:::text)))
                  ORDER BY criado_em DESC LIMIT ' . self::MAX_LIGADOS_MOSTRADOS,
                ['modulo' => $ligacao['modulo_id'], 'campo' => $ligacao['id'], 'item' => $registroId]
            )->getResultArray();
            if ($linhas === []) {
                continue;
            }

            $titulos  = $this->titulosDosItens($ligacao['modulo_id'], array_column($linhas, 'id'));
            $grupos[] = [
                'campo_id'   => $ligacao['id'],
                'campo_nome' => $ligacao['nome'],
                'modulo'     => ['id' => $ligacao['modulo_id'], 'nome' => $ligacao['modulo_nome'], 'icone' => $ligacao['modulo_icone']],
                'total'      => (int) $linhas[0]['total'],
                'itens'      => array_map(static fn ($l) => ['id' => $l['id'], 'titulo' => $titulos[$l['id']] ?? null], $linhas),
            ];
        }

        return $grupos;
    }

    /** Itens da tabela como { id, titulo }, para escolher e mostrar ligações. */
    public function listarTitulos(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $this->confirmarModuloDaEmpresa($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        $lista = [];
        foreach ($this->titulosDosItens($moduloId) as $id => $titulo) {
            $lista[] = ['id' => $id, 'titulo' => $titulo];
        }

        return $lista;
    }

    /**
     * Título de cada item: o valor da primeira coluna de texto (ou da primeira
     * coluna, se não houver texto). É o "nome" do item onde ele aparece ligado.
     * Sem checagem de acesso: quem chama decide.
     *
     * @param array<mixed>|null $ids só estes itens (null = todos)
     *
     * @return array<string, string|null> id => título, em ordem alfabética
     */
    public function titulosDosItens(string $moduloId, ?array $ids = null): array
    {
        if ($ids !== null) {
            $ids = array_values(array_filter($ids, static fn ($id) => is_string($id) && Filtros::uuidValido($id)));
            if ($ids === []) {
                return [];
            }
        }

        $campos = $this->campoModuloModel->select('id, tipo')->where('modulo_id', $moduloId)->orderBy('ordem', 'ASC')->findAll();
        $titulo = self::campoDeTitulo($campos) !== null ? 'dados->>:campo:' : 'NULL';

        $linhas = db_connect()->query(
            "SELECT id, {$titulo} AS titulo FROM registro WHERE modulo_id = :modulo:"
            . ($ids !== null ? ' AND id IN :ids:' : '')
            . ($titulo !== 'NULL' ? " ORDER BY lower({$titulo}) NULLS LAST, criado_em" : ' ORDER BY criado_em'),
            ['modulo' => $moduloId, 'campo' => (string) self::campoDeTitulo($campos), 'ids' => $ids ?? []]
        )->getResultArray();

        return array_column($linhas, 'titulo', 'id');
    }

    /** A coluna que dá nome aos itens: a primeira de texto, ou a primeira de todas. */
    public static function campoDeTitulo(array $camposEmOrdem): ?string
    {
        foreach ($camposEmOrdem as $campo) {
            if ($campo['tipo'] === 'texto') {
                return $campo['id'];
            }
        }

        return $camposEmOrdem[0]['id'] ?? null;
    }

    /**
     * @throws NaoEncontradoException se o registro não existir, ou não
     *         pertencer ao módulo/empresa informados
     */
    public function buscarRegistro(string $registroId, string $moduloId, string $empresaId): array
    {
        $registro = $this->registroModel
            ->select('registro.*')
            ->join('modulo', 'modulo.id = registro.modulo_id')
            ->where('registro.id', $registroId)
            ->where('registro.modulo_id', $moduloId)
            ->where('modulo.empresa_id', $empresaId)
            ->first();

        if (! $registro) {
            throw new NaoEncontradoException('Item não encontrado.');
        }

        return $registro;
    }

    public function moduloDaEmpresa(string $moduloId, string $empresaId): array
    {
        $modulo = $this->moduloModel
            ->where('id', $moduloId)
            ->where('empresa_id', $empresaId)
            ->first();

        if (! $modulo) {
            throw new NaoEncontradoException('Tabela não encontrada.');
        }

        return $modulo;
    }

    /** Confere que a tabela é da empresa; com `$comCampos`, devolve as colunas dela. */
    public function confirmarModuloDaEmpresa(string $moduloId, string $empresaId, bool $comCampos = false): ?array
    {
        $this->moduloDaEmpresa($moduloId, $empresaId);

        if (! $comCampos) {
            return null;
        }

        return $this->campoModuloModel->where('modulo_id', $moduloId)->findAll();
    }
}
