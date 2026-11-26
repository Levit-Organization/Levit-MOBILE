<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\CampoModuloModel;
use App\Models\ModuloModel;
use App\Models\RegistroModel;
use App\Services\Registros\ConsultaDeRegistros;
use App\Services\Registros\ValidadorDeDados;
use CodeIgniter\Events\Events;

/**
 * Itens das tabelas de dados: criar, alterar, importar e excluir (pela tela e
 * pelas automações). A leitura fica em Registros\ConsultaDeRegistros e a
 * validação dos dados em Registros\ValidadorDeDados; os métodos de leitura
 * daqui só repassam para lá.
 */
class RegistroService
{
    /** Máximo de itens mostrados por ligação recebida (o total vem à parte). */
    public const MAX_LIGADOS_MOSTRADOS = ConsultaDeRegistros::MAX_LIGADOS_MOSTRADOS;

    /** Máximo de itens por importação (uma planilha maior vai em partes). */
    public const MAX_IMPORTACAO = 1000;

    /** Máximo de itens numa coluna de ligação que aceita vários. */
    public const MAX_LIGADOS = ValidadorDeDados::MAX_LIGADOS;

    protected ModuloModel $moduloModel;
    protected CampoModuloModel $campoModuloModel;
    protected RegistroModel $registroModel;
    protected AutorizacaoModuloService $autorizacaoModuloService;
    protected ConsultaDeRegistros $consulta;

    /** Um por instância: guarda o que já foi conferido nesta requisição. */
    protected ValidadorDeDados $validador;

    public function __construct()
    {
        $this->moduloModel              = new ModuloModel();
        $this->campoModuloModel         = new CampoModuloModel();
        $this->registroModel            = new RegistroModel();
        $this->autorizacaoModuloService = new AutorizacaoModuloService();
        $this->consulta                 = new ConsultaDeRegistros($this->moduloModel, $this->campoModuloModel, $this->registroModel, $this->autorizacaoModuloService);
        $this->validador                = new ValidadorDeDados();
    }

    // ------------------------------------------------------------------
    // Leitura (ver Registros\ConsultaDeRegistros)
    // ------------------------------------------------------------------

    /** Itens da tabela, mais novos primeiro, com busca e pesquisa avançada. */
    public function listarRegistros(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, ?string $busca = null, ?array $filtros = null): array
    {
        return $this->consulta->listarRegistros($moduloId, $empresaId, $cargoId, $acessoTotal, $busca, $filtros);
    }

    /**
     * O outro lado das ligações: itens que apontam para este, por coluna.
     *
     * @return list<array{campo_id: string, campo_nome: string, modulo: array, total: int, itens: list<array{id: string, titulo: ?string}>}>
     */
    public function itensQueApontam(string $registroId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        return $this->consulta->itensQueApontam($registroId, $moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    /** Itens da tabela como { id, titulo }, para escolher e mostrar ligações. */
    public function listarTitulos(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        return $this->consulta->listarTitulos($moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    /**
     * Título de cada item (sem checagem de acesso: quem chama decide).
     *
     * @param array<mixed>|null $ids só estes itens (null = todos)
     *
     * @return array<string, string|null> id => título, em ordem alfabética
     */
    public function titulosDosItens(string $moduloId, ?array $ids = null): array
    {
        return $this->consulta->titulosDosItens($moduloId, $ids);
    }

    /** A coluna que dá nome aos itens: a primeira de texto, ou a primeira de todas. */
    public static function campoDeTitulo(array $camposEmOrdem): ?string
    {
        return ConsultaDeRegistros::campoDeTitulo($camposEmOrdem);
    }

    /**
     * @throws NaoEncontradoException se o registro não existir, ou não
     *         pertencer ao módulo/empresa informados
     */
    public function buscarRegistro(string $registroId, string $moduloId, string $empresaId): array
    {
        return $this->consulta->buscarRegistro($registroId, $moduloId, $empresaId);
    }

    // ------------------------------------------------------------------
    // Escrita
    // ------------------------------------------------------------------

    public function criarRegistro(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $usuarioId, array $dados): array
    {
        $campos = $this->consulta->confirmarModuloDaEmpresa($moduloId, $empresaId, true);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $dadosValidados = $this->validador->validar($campos, $dados, $moduloId);

        $registroId = $this->registroModel->insert([
            'modulo_id'  => $moduloId,
            'dados'      => $dadosValidados,
            'criado_por' => $usuarioId,
        ]);

        Events::trigger('registro_criado', [
            'modulo_id'      => $moduloId,
            'registro_id'    => $registroId,
            'dados_registro' => $dadosValidados,
        ]);

        return $this->buscarRegistro($registroId, $moduloId, $empresaId);
    }

    public function atualizarRegistro(string $registroId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $usuarioId, array $dados): array
    {
        $anterior = $this->buscarRegistro($registroId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $campos         = $this->consulta->confirmarModuloDaEmpresa($moduloId, $empresaId, true);
        $dadosValidados = $this->validador->validar($campos, $dados, $moduloId);

        $this->registroModel->update($registroId, [
            'dados'          => $dadosValidados,
            'atualizado_por' => $usuarioId,
        ]);

        Events::trigger('registro_atualizado', [
            'modulo_id'        => $moduloId,
            'registro_id'      => $registroId,
            'dados_registro'   => $dadosValidados,
            'dados_anteriores' => $anterior['dados'] ?? [],
        ]);

        // Item que se repete e acabou de ser concluído: nasce a próxima ocorrência.
        (new RecorrenciaService())->depoisDeSalvar($registroId, $moduloId, $campos, $anterior['dados'] ?? [], $dadosValidados, $usuarioId);

        return $this->buscarRegistro($registroId, $moduloId, $empresaId);
    }

    /**
     * Importa itens de uma planilha. Cada linha passa pela mesma validação de
     * um item criado à mão; as válidas entram numa transação só e as inválidas
     * voltam com o número da linha e o motivo.
     *
     * Não dispara automações: importar 500 clientes não deve mandar 500
     * e-mails. Fica uma linha no histórico de atividades.
     *
     * @param array<int, array{linha?: int, dados?: array<string, mixed>}> $itens
     *
     * @return array{criados: int, erros: array<int, array{linha: int, mensagem: string}>}
     */
    public function importar(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $usuarioId, array $itens): array
    {
        $campos = $this->consulta->confirmarModuloDaEmpresa($moduloId, $empresaId, true);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        if ($itens === []) {
            throw new \DomainException('A planilha não tem nenhuma linha para importar.');
        }
        if (count($itens) > self::MAX_IMPORTACAO) {
            throw new \DomainException('Importe no máximo ' . self::MAX_IMPORTACAO . ' linhas por vez.');
        }

        $this->validador->preconferirLigacoes($campos, $itens);

        helper('uuid');
        $agora   = gmdate('Y-m-d H:i:s');
        $validos = [];
        $erros   = [];
        foreach (array_values($itens) as $i => $item) {
            $linha = (int) ($item['linha'] ?? $i + 2); // linha 1 é o cabeçalho
            try {
                $dados = $this->validador->validar($campos, ['dados' => (array) ($item['dados'] ?? [])], $moduloId);
                if ($dados === []) {
                    throw new \DomainException('Linha vazia.');
                }
                $validos[] = [
                    'id'            => generate_uuid_v7(),
                    'modulo_id'     => $moduloId,
                    'dados'         => json_encode($dados, JSON_UNESCAPED_UNICODE),
                    'criado_por'    => $usuarioId,
                    'criado_em'     => $agora,
                    'atualizado_em' => $agora,
                ];
            } catch (\DomainException $e) {
                $erros[] = ['linha' => $linha, 'mensagem' => $e->getMessage()];
            }
        }

        if ($validos !== []) {
            $db = db_connect();
            $db->transStart();
            foreach (array_chunk($validos, 200) as $lote) {
                $db->table('registro')->insertBatch($lote);
            }
            $db->transComplete();
            if ($db->transStatus() === false) {
                throw new \RuntimeException('Não foi possível importar agora. Nada foi gravado; tente de novo.');
            }

            AtividadeService::registrar('itens_importados', 'Importou ' . count($validos) . (count($validos) === 1 ? ' item' : ' itens') . ' de uma planilha', $moduloId);
        }

        return ['criados' => count($validos), 'erros' => $erros];
    }

    public function excluirRegistro(string $registroId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $registro = $this->buscarRegistro($registroId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $db = db_connect();
        $db->transStart();
        $this->registroModel->delete($registroId);
        $this->desvincular($registroId, $moduloId);
        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível excluir o item. Tente novamente.');
        }

        Events::trigger('registro_excluido', [
            'modulo_id'      => $moduloId,
            'registro_id'    => $registroId,
            'dados_registro' => $registro['dados'],
        ]);
    }

    /**
     * O item excluído sai das colunas de ligação que apontavam para ele, como
     * um ON DELETE SET NULL: quem ligava a vários perde só esse; quem ligava
     * só a ele fica vazio.
     */
    private function desvincular(string $registroId, string $moduloId): void
    {
        $campos = $this->campoModuloModel->select('id, modulo_id')->where('modulo_vinculado_id', $moduloId)->findAll();
        $db     = db_connect();

        foreach ($campos as $campo) {
            $db->query(
                "UPDATE registro SET dados = CASE
                    WHEN jsonb_typeof(dados->:campo:) = 'array' AND jsonb_array_length((dados->:campo:) - :item:::text) > 0
                        THEN jsonb_set(dados, ARRAY[:campo:]::text[], (dados->:campo:) - :item:::text)
                    ELSE dados - :campo:::text
                 END
                 WHERE modulo_id = :modulo:
                   AND (dados @> jsonb_build_object(:campo:::text, :item:::text)
                     OR dados @> jsonb_build_object(:campo:::text, jsonb_build_array(:item:::text)))",
                ['campo' => $campo['id'], 'item' => $registroId, 'modulo' => $campo['modulo_id']]
            );
        }
    }

    /**
     * Ação "atualizar campo" de uma automação. Sem checagem de permissão (quem
     * configurou a automação já tinha), mas com a mesma validação de tipos.
     * Não grava nem dispara nada se o valor já for o mesmo: isso, somado à
     * `cadeia`, impede que automações fiquem se redisparando.
     *
     * @param array<string, mixed> $alteracoes campo_id => valor
     * @param string[]             $cadeia     automações que levaram a esta alteração
     *
     * @return bool se algo mudou
     */
    public function atualizarPorAutomacao(string $registroId, string $moduloId, array $alteracoes, array $cadeia): bool
    {
        $registro = $this->registroModel->where('id', $registroId)->where('modulo_id', $moduloId)->first();
        if (! $registro) {
            throw new NaoEncontradoException('O item não existe mais.');
        }

        $anteriores = $registro['dados'] ?? [];
        $campos     = $this->campoModuloModel->where('modulo_id', $moduloId)->findAll();
        $novos      = $this->validador->validar($campos, ['dados' => $alteracoes + $anteriores], $moduloId);

        // Campo esvaziado pela automação: validar descarta vazios, então remove aqui.
        foreach ($alteracoes as $campoId => $valor) {
            if ($valor === null || $valor === '') {
                unset($novos[$campoId]);
            }
        }

        if ($novos == $anteriores) {
            return false;
        }

        $this->registroModel->update($registroId, ['dados' => $novos]);

        Events::trigger('registro_atualizado', [
            'modulo_id'        => $moduloId,
            'registro_id'      => $registroId,
            'dados_registro'   => $novos,
            'dados_anteriores' => $anteriores,
            'cadeia'           => $cadeia,
        ]);

        (new RecorrenciaService())->depoisDeSalvar($registroId, $moduloId, $campos, $anteriores, $novos, null);

        return true;
    }

    /**
     * Ação "criar registro" de uma automação, em qualquer módulo de dados da
     * mesma empresa.
     *
     * @return string id do registro criado
     */
    public function criarPorAutomacao(string $moduloId, array $dados, array $cadeia): string
    {
        $campos         = $this->campoModuloModel->where('modulo_id', $moduloId)->findAll();
        $dadosValidados = $this->validador->validar($campos, ['dados' => $dados], $moduloId);

        if ($dadosValidados === []) {
            throw new \DomainException('Nenhuma coluna preenchida para o novo item.');
        }

        $registroId = $this->registroModel->insert([
            'modulo_id' => $moduloId,
            'dados'     => $dadosValidados,
        ]);

        Events::trigger('registro_criado', [
            'modulo_id'      => $moduloId,
            'registro_id'    => $registroId,
            'dados_registro' => $dadosValidados,
            'cadeia'         => $cadeia,
        ]);

        return $registroId;
    }
}
