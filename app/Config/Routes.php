<?php

use CodeIgniter\Router\RouteCollection;

/** @var RouteCollection $routes */
$routes->get('/', 'Home::index');

$routes->group('api/v1', function ($routes) {
    $uuid = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';

    $routes->set404Override('App\Controllers\Home::naoEncontrado');

    $routes->post('auth/registrar', 'AuthController::registrar', ['filter' => 'ratelimit:registro,10']);
    $routes->post('auth/login', 'AuthController::login', ['filter' => 'ratelimit:login,30,10']);
    $routes->post('auth/forgot-password', 'AuthController::esqueciSenha', ['filter' => 'ratelimit:senha,5,3']);
    $routes->post('auth/reset-password', 'AuthController::redefinirSenha', ['filter' => 'ratelimit:senha_nova,10']);
    $routes->post('auth/logout', 'AuthController::logout', ['filter' => 'auth']);
    // Uma pessoa, várias equipes: listar, trocar a equipe ativa e criar outra.
    $routes->get('auth/equipes', 'AuthController::equipes', ['filter' => 'auth']);
    $routes->post('auth/equipes', 'AuthController::criarEquipe', ['filter' => 'auth']);
    $routes->post('auth/trocar-equipe', 'AuthController::trocarEquipe', ['filter' => 'auth']);

    $routes->get('modulos', 'ModuloController::listar', ['filter' => 'auth']);
    $routes->post('modulos', 'ModuloController::criar', ['filter' => 'auth:criar_modulos']);
    $routes->get("modulos/({$uuid})", 'ModuloController::detalhes/$1', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})", 'ModuloController::atualizar/$1', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})", 'ModuloController::excluir/$1', ['filter' => 'auth']);
    $routes->post("modulos/({$uuid})/campos", 'ModuloController::adicionarCampo/$1', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/campos/reordenar", 'ModuloController::reordenarCampos/$1', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/campos/({$uuid})", 'ModuloController::atualizarCampo/$1/$2', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})/campos/({$uuid})", 'ModuloController::excluirCampo/$1/$2', ['filter' => 'auth']);

    $routes->get("modulos/({$uuid})/registros", 'RegistroController::listar/$1', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/titulos", 'RegistroController::titulos/$1', ['filter' => 'auth']);
    $routes->post("modulos/({$uuid})/registros", 'RegistroController::criar/$1', ['filter' => 'auth']);
    $routes->post("modulos/({$uuid})/registros/importar", 'RegistroController::importar/$1', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/registros/({$uuid})/ligados", 'RegistroController::ligados/$1/$2', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/registros/({$uuid})", 'RegistroController::atualizar/$1/$2', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})/registros/({$uuid})", 'RegistroController::excluir/$1/$2', ['filter' => 'auth']);

    $routes->post("modulos/({$uuid})/arquivos", 'ArquivoController::enviar/$1', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/arquivos/({$uuid})", 'ArquivoController::baixar/$1/$2', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})/arquivos/({$uuid})", 'ArquivoController::excluir/$1/$2', ['filter' => 'auth']);

    $routes->get("publico/vagas/({$uuid})", 'CandidatoController::vagaPublica/$1', ['filter' => 'ratelimit']);
    $routes->post("publico/candidatura/({$uuid})", 'CandidatoController::candidatar/$1', ['filter' => 'ratelimit']);
    $routes->post("modulos/({$uuid})/candidatos", 'CandidatoController::adicionar/$1', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/candidatos/({$uuid})/curriculo", 'CandidatoController::curriculo/$1/$2', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/kanban", 'CandidatoController::kanban/$1', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/candidatos/({$uuid})", 'CandidatoController::detalhes/$1/$2', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/candidatos/({$uuid})/fase", 'CandidatoController::moverFase/$1/$2', ['filter' => 'auth']);
    $routes->get('recrutamento/kanban', 'CandidatoController::kanbanGlobal', ['filter' => 'auth']);
    $routes->put("recrutamento/candidatos/({$uuid})/fase", 'CandidatoController::moverFaseGlobal/$1', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})/candidatos/({$uuid})", 'CandidatoController::excluir/$1/$2', ['filter' => 'auth']);

    $routes->post("modulos/({$uuid})/fases", 'ModuloController::adicionarFase/$1', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/fases/reordenar", 'ModuloController::reordenarFases/$1', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/fases/({$uuid})", 'ModuloController::atualizarFase/$1/$2', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})/fases/({$uuid})", 'ModuloController::excluirFase/$1/$2', ['filter' => 'auth']);

    $routes->get("modulos/({$uuid})/lancamentos", 'FinanceiroController::listarLancamentos/$1', ['filter' => 'auth']);
    $routes->post("modulos/({$uuid})/lancamentos", 'FinanceiroController::criarLancamento/$1', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/lancamentos/({$uuid})", 'FinanceiroController::atualizarLancamento/$1/$2', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})/lancamentos/({$uuid})", 'FinanceiroController::excluirLancamento/$1/$2', ['filter' => 'auth']);

    $routes->get('financeiro/categorias', 'FinanceiroController::listarCategorias', ['filter' => 'auth']);
    $routes->post('financeiro/categorias', 'FinanceiroController::criarCategoria', ['filter' => 'auth']);
    $routes->delete("financeiro/categorias/({$uuid})", 'FinanceiroController::excluirCategoria/$1', ['filter' => 'auth']);

    $routes->get('cargos', 'CargoController::listar', ['filter' => 'auth:gerenciar_cargos']);
    $routes->post('cargos', 'CargoController::criar', ['filter' => 'auth:gerenciar_cargos']);
    $routes->put("cargos/({$uuid})", 'CargoController::atualizar/$1', ['filter' => 'auth:gerenciar_cargos']);
    $routes->delete("cargos/({$uuid})", 'CargoController::excluir/$1', ['filter' => 'auth:gerenciar_cargos']);

    $routes->post('equipe/convidar', 'EquipeController::convidar', ['filter' => 'auth:gerenciar_equipe']);
    $routes->get('equipe', 'EquipeController::listarMembros', ['filter' => 'auth:visualizar_equipe']);
    $routes->put("equipe/({$uuid})", 'EquipeController::atualizarMembro/$1', ['filter' => 'auth:gerenciar_equipe']);
    $routes->delete("equipe/({$uuid})", 'EquipeController::removerMembro/$1', ['filter' => 'auth:gerenciar_equipe']);
    // Relatórios agendados em PDF (acesso total; conferido no controller).
    $routes->get('relatorios', 'RelatorioController::listar', ['filter' => 'auth']);
    $routes->post('relatorios', 'RelatorioController::criar', ['filter' => 'auth']);
    $routes->get('relatorios/pdf', 'RelatorioController::pdf', ['filter' => 'auth']);
    $routes->get('relatorios/indicadores', 'RelatorioController::indicadores', ['filter' => 'auth']);
    $routes->get('relatorios/tabelas', 'RelatorioController::tabelas', ['filter' => 'auth']);
    $routes->get('relatorios/agrupar', 'RelatorioController::agrupar', ['filter' => 'auth']);
    $routes->get('relatorios/agrupar/pdf', 'RelatorioController::agruparPdf', ['filter' => 'auth']);
    $routes->put("relatorios/({$uuid})", 'RelatorioController::atualizar/$1', ['filter' => 'auth']);
    $routes->delete("relatorios/({$uuid})", 'RelatorioController::excluir/$1', ['filter' => 'auth']);
    $routes->get("relatorios/({$uuid})/envios", 'RelatorioController::envios/$1', ['filter' => 'auth']);
    $routes->post("relatorios/({$uuid})/enviar-agora", 'RelatorioController::enviarAgora/$1', ['filter' => 'auth']);

    // Nomes da equipe para escolher o responsável de um item (sem e-mail; não exige ver a equipe).
    $routes->get('equipe/pessoas', 'EquipeController::listarPessoas', ['filter' => 'auth']);
    // Prazos de todas as tabelas visíveis: atrasados, hoje e próximos 7 dias.
    $routes->get('itens/prazos', 'PrazoController::listar', ['filter' => 'auth']);
    $routes->get('equipe/convites', 'EquipeController::listarConvites', ['filter' => 'auth:visualizar_equipe']);
    $routes->post("equipe/convites/({$uuid})/reenviar", 'EquipeController::reenviarConvite/$1', ['filter' => 'auth:gerenciar_equipe']);
    $routes->delete("equipe/convites/({$uuid})", 'EquipeController::cancelarConvite/$1', ['filter' => 'auth:gerenciar_equipe']);
    $routes->get('publico/convite/([0-9a-f]{64})', 'EquipeController::detalhesDoConvite/$1', ['filter' => 'ratelimit:convite,60']);
    $routes->post('publico/convite/aceitar', 'EquipeController::aceitarConvite', ['filter' => 'ratelimit:convite_aceite,10']);

    $routes->post("modulos/({$uuid})/automacoes", 'AutomacaoController::criar/$1', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/automacoes", 'AutomacaoController::listar/$1', ['filter' => 'auth']);
    $routes->get('notificacoes', 'NotificacaoController::listar', ['filter' => 'auth']);
    $routes->get('notificacoes/contagem', 'NotificacaoController::contagem', ['filter' => 'auth']);
    $routes->put('notificacoes/lidas', 'NotificacaoController::marcarTodasLidas', ['filter' => 'auth']);
    $routes->put("notificacoes/({$uuid})/lida", 'NotificacaoController::marcarLida/$1', ['filter' => 'auth']);
    $routes->delete("notificacoes/({$uuid})", 'NotificacaoController::excluir/$1', ['filter' => 'auth']);

    $routes->get('automacoes', 'AutomacaoController::listarTodas', ['filter' => 'auth']);
    $routes->post("modulos/({$uuid})/automacoes/simular", 'AutomacaoController::simular/$1', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/automacoes/({$uuid})", 'AutomacaoController::atualizar/$1/$2', ['filter' => 'auth']);
    $routes->get("modulos/({$uuid})/automacoes/({$uuid})/execucoes", 'AutomacaoController::execucoes/$1/$2', ['filter' => 'auth']);
    $routes->post("modulos/({$uuid})/automacoes/({$uuid})/execucoes/({$uuid})/reexecutar", 'AutomacaoController::reexecutar/$1/$2/$3', ['filter' => 'auth']);
    $routes->put("modulos/({$uuid})/automacoes/({$uuid})/ativo", 'AutomacaoController::alternarAtivo/$1/$2', ['filter' => 'auth']);
    $routes->delete("modulos/({$uuid})/automacoes/({$uuid})", 'AutomacaoController::excluir/$1/$2', ['filter' => 'auth']);

    $routes->get('backup/json', 'BackupController::exportarJson', ['filter' => 'auth:exportar_dados']);
    $routes->get("modulos/({$uuid})/exportar-csv", 'BackupController::exportarCsv/$1', ['filter' => 'auth:exportar_dados']);
    $routes->post('backup/resetar', 'BackupController::resetarFabrica', ['filter' => 'auth:resetar_dados']);

    $routes->get("cargos/({$uuid})/modulos", 'CargoModuloPermissaoController::listar/$1', ['filter' => 'auth:gerenciar_cargos']);
    $routes->put("cargos/({$uuid})/modulos/({$uuid})", 'CargoModuloPermissaoController::definir/$1/$2', ['filter' => 'auth:gerenciar_cargos']);
    $routes->delete("cargos/({$uuid})/modulos/({$uuid})", 'CargoModuloPermissaoController::remover/$1/$2', ['filter' => 'auth:gerenciar_cargos']);
});

$routes->get('api/v1/seed/categorias', 'SeedController::seedCategorias');
