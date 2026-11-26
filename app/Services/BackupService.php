<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\ArquivoModel;
use App\Models\CampoModuloModel;
use App\Models\CandidatoModel;
use App\Models\CargoModel;
use App\Models\CargoPermissaoModel;
use App\Models\EmpresaModel;
use App\Models\FaseRecrutamentoModel;
use App\Models\HistoricoFaseModel;
use App\Models\ModuloModel;
use App\Models\RegistroModel;

class BackupService
{
    protected EmpresaModel $empresaModel;
    protected CargoModel $cargoModel;
    protected CargoPermissaoModel $cargoPermissaoModel;
    protected ModuloModel $moduloModel;
    protected CampoModuloModel $campoModuloModel;
    protected RegistroModel $registroModel;
    protected ArquivoModel $arquivoModel;
    protected FaseRecrutamentoModel $faseRecrutamentoModel;
    protected CandidatoModel $candidatoModel;
    protected HistoricoFaseModel $historicoFaseModel;

    public function __construct()
    {
        $this->empresaModel          = new EmpresaModel();
        $this->cargoModel            = new CargoModel();
        $this->cargoPermissaoModel   = new CargoPermissaoModel();
        $this->moduloModel           = new ModuloModel();
        $this->campoModuloModel      = new CampoModuloModel();
        $this->registroModel         = new RegistroModel();
        $this->arquivoModel          = new ArquivoModel();
        $this->faseRecrutamentoModel = new FaseRecrutamentoModel();
        $this->candidatoModel        = new CandidatoModel();
        $this->historicoFaseModel    = new HistoricoFaseModel();
    }

    /**
     * Monta o backup completo da empresa — nunca inclui senha (nem hash).
     */
    public function exportarJson(string $empresaId): array
    {
        $empresa = $this->empresaModel->find($empresaId);

        if (! $empresa) {
            throw new NaoEncontradoException('Empresa não encontrada.');
        }

        // Quem faz parte desta equipe, com o cargo daqui (criado_em = entrada na equipe).
        $usuarios = db_connect()->table('usuario_equipe')
            ->select('id, empresa_id, cargo_id, nome, email, criado_em')
            ->where('empresa_id', $empresaId)
            ->get()->getResultArray();

        $cargos   = $this->cargoModel->where('empresa_id', $empresaId)->findAll();
        $cargoIds = array_column($cargos, 'id');

        $cargoPermissoes = empty($cargoIds) ? [] : $this->cargoPermissaoModel
            ->select('cargo_permissao.cargo_id, permissao.codigo')
            ->join('permissao', 'permissao.id = cargo_permissao.permissao_id')
            ->whereIn('cargo_permissao.cargo_id', $cargoIds)
            ->findAll();

        $modulos   = $this->moduloModel->where('empresa_id', $empresaId)->findAll();
        $moduloIds = array_column($modulos, 'id');

        $campos     = empty($moduloIds) ? [] : $this->campoModuloModel->whereIn('modulo_id', $moduloIds)->findAll();
        $registros  = empty($moduloIds) ? [] : $this->registroModel->whereIn('modulo_id', $moduloIds)->findAll();
        $arquivos   = empty($registros) ? [] : $this->arquivoModel->whereIn('registro_id', array_column($registros, 'id'))->findAll();
        $fases      = empty($moduloIds) ? [] : $this->faseRecrutamentoModel->whereIn('modulo_id', $moduloIds)->findAll();
        $candidatos = empty($moduloIds) ? [] : $this->candidatoModel->whereIn('modulo_id', $moduloIds)->findAll();
        $historico  = empty($candidatos) ? [] : $this->historicoFaseModel->whereIn('candidato_id', array_column($candidatos, 'id'))->findAll();

        return [
            'gerado_em'          => date('Y-m-d H:i:s'),
            'empresa'            => $empresa,
            'usuarios'           => $usuarios,
            'cargos'             => $cargos,
            'cargo_permissoes'   => $cargoPermissoes,
            'modulos'            => $modulos,
            'campos_modulo'      => $campos,
            'registros'          => $registros,
            'arquivos'           => $arquivos,
            'fases_recrutamento' => $fases,
            'candidatos'         => $candidatos,
            'historico_fase'     => $historico,
        ];
    }

    /**
     * @return array{nome_modulo: string, cabecalho: array, linhas: array}
     */
    public function exportarCsvModulo(string $moduloId, string $empresaId): array
    {
        $modulo = $this->moduloModel->where('id', $moduloId)->where('empresa_id', $empresaId)->first();

        if (! $modulo) {
            throw new NaoEncontradoException('Tabela não encontrada.');
        }

        $campos    = $this->campoModuloModel->where('modulo_id', $moduloId)->orderBy('ordem', 'ASC')->findAll();
        $registros = $this->registroModel->where('modulo_id', $moduloId)->orderBy('criado_em', 'ASC')->findAll();

        $cabecalho = ['Criado em'];
        foreach ($campos as $campo) {
            $cabecalho[] = self::celulaSegura($campo['nome']);
        }

        // Colunas de ligação saem com o nome dos itens ligados, não com o identificador.
        $titulos = [];
        foreach ($campos as $campo) {
            if ($campo['tipo'] === 'vinculo' && ! empty($campo['modulo_vinculado_id'])) {
                $titulos[$campo['id']] ??= (new RegistroService())->titulosDosItens($campo['modulo_vinculado_id']);
            }
        }

        $linhas = [];
        foreach ($registros as $registro) {
            $linha = [$registro['criado_em']];
            foreach ($campos as $campo) {
                $valor = $registro['dados'][$campo['id']] ?? '';
                if (isset($titulos[$campo['id']])) {
                    $valor = implode(', ', array_map(static fn ($id) => $titulos[$campo['id']][$id] ?? '', (array) $valor));
                }
                $linha[] = self::celulaSegura(is_array($valor) ? json_encode($valor) : (string) $valor);
            }
            $linhas[] = $linha;
        }

        return ['nome_modulo' => $modulo['nome'], 'cabecalho' => $cabecalho, 'linhas' => $linhas];
    }

    public function podeResetar(string $empresaId, string $usuarioId): bool
    {
        $empresa = $this->empresaModel->find($empresaId);

        return $empresa !== null && $empresa['administrador_principal_id'] === $usuarioId;
    }

    /**
     * Apaga todos os módulos da empresa — o CASCADE já configurado desde
     * as primeiras migrations cuida de arrastar campos, registros,
     * arquivos, candidatos, fases e automações junto, automaticamente.
     */
    public function resetarFabrica(string $empresaId): void
    {
        $moduloIds = array_column($this->moduloModel->where('empresa_id', $empresaId)->findAll(), 'id');

        if (! empty($moduloIds)) {
            $registroIds = array_column($this->registroModel->whereIn('modulo_id', $moduloIds)->findAll(), 'id');

            if (! empty($registroIds)) {
                foreach ($this->arquivoModel->whereIn('registro_id', $registroIds)->findAll() as $arquivo) {
                    try {
                        service('storage')->excluir($arquivo['chave_armazenamento']);
                    } catch (\RuntimeException $e) {
                        log_message('error', "Falha ao excluir arquivo remoto no reset: {$arquivo['chave_armazenamento']} — {$e->getMessage()}");
                    }
                }
            }
        }

        $this->moduloModel->where('empresa_id', $empresaId)->delete();
    }

    /**
     * Excel e LibreOffice executam células que começam com = + - @ (ex.:
     * =HYPERLINK(...) ou =cmd|...). Como os valores vêm de quem preenche a
     * tabela (inclusive do formulário público de candidatura), essas células
     * ganham um apóstrofo na frente e abrem como texto. Números ficam como estão.
     */
    public static function celulaSegura(string $valor): string
    {
        if ($valor === '' || is_numeric($valor)) {
            return $valor; // número negativo continua número
        }

        return in_array($valor[0], ['=', '+', '-', '@', "\t", "\r"], true) ? "'" . $valor : $valor;
    }
}
