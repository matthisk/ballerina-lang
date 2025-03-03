/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler.semantics.analyzer;

import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.compiler.CompilerOptionName;
import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.MarkdownDocAttachment;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.symbols.SymbolKind;
import org.ballerinalang.model.symbols.SymbolOrigin;
import org.ballerinalang.model.tree.IdentifierNode;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.OrderedNode;
import org.ballerinalang.model.tree.TopLevelNode;
import org.ballerinalang.model.tree.TypeDefinition;
import org.ballerinalang.model.tree.statements.StatementNode;
import org.ballerinalang.model.tree.types.TypeNode;
import org.ballerinalang.model.types.SelectivelyImmutableReferenceType;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.wso2.ballerinalang.compiler.PackageCache;
import org.wso2.ballerinalang.compiler.PackageLoader;
import org.wso2.ballerinalang.compiler.SourceDirectory;
import org.wso2.ballerinalang.compiler.desugar.ASTBuilderUtil;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.parser.BLangAnonymousModelHelper;
import org.wso2.ballerinalang.compiler.parser.BLangMissingNodesHelper;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.Scope.ScopeEntry;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAnnotationSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAttachedFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BClassSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BConstantSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BEnumSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BErrorTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BRecordTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BResourceFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BServiceSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BXMLAttributeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BXMLNSSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BAnnotationType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BErrorType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BFutureType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BIntersectionType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BMapType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BObjectType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BRecordType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStructureType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTupleType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTypeIdSet;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotation;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotationAttachment;
import org.wso2.ballerinalang.compiler.tree.BLangClassDefinition;
import org.wso2.ballerinalang.compiler.tree.BLangCompilationUnit;
import org.wso2.ballerinalang.compiler.tree.BLangErrorVariable;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangInvokableNode;
import org.wso2.ballerinalang.compiler.tree.BLangMarkdownDocumentation;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangRecordVariable;
import org.wso2.ballerinalang.compiler.tree.BLangResource;
import org.wso2.ballerinalang.compiler.tree.BLangResourceFunction;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTestablePackage;
import org.wso2.ballerinalang.compiler.tree.BLangTupleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTypeDefinition;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.BLangWorker;
import org.wso2.ballerinalang.compiler.tree.BLangXMLNS;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangConstant;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMarkDownDeprecatedParametersDocumentation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMarkDownDeprecationDocumentation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMarkdownParameterDocumentation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLAttribute;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLQName;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangXMLNSStatement;
import org.wso2.ballerinalang.compiler.tree.types.BLangArrayType;
import org.wso2.ballerinalang.compiler.tree.types.BLangConstrainedType;
import org.wso2.ballerinalang.compiler.tree.types.BLangErrorType;
import org.wso2.ballerinalang.compiler.tree.types.BLangFiniteTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangFunctionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangIntersectionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangObjectTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangRecordTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangStructureTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangTableTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangTupleTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangType;
import org.wso2.ballerinalang.compiler.tree.types.BLangUnionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;
import org.wso2.ballerinalang.compiler.util.BArrayState;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;
import org.wso2.ballerinalang.compiler.util.ImmutableTypeCloner;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.util.Flags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;

import static org.ballerinalang.model.elements.PackageID.ARRAY;
import static org.ballerinalang.model.elements.PackageID.BOOLEAN;
import static org.ballerinalang.model.elements.PackageID.DECIMAL;
import static org.ballerinalang.model.elements.PackageID.ERROR;
import static org.ballerinalang.model.elements.PackageID.FLOAT;
import static org.ballerinalang.model.elements.PackageID.FUTURE;
import static org.ballerinalang.model.elements.PackageID.INT;
import static org.ballerinalang.model.elements.PackageID.MAP;
import static org.ballerinalang.model.elements.PackageID.OBJECT;
import static org.ballerinalang.model.elements.PackageID.QUERY;
import static org.ballerinalang.model.elements.PackageID.STREAM;
import static org.ballerinalang.model.elements.PackageID.STRING;
import static org.ballerinalang.model.elements.PackageID.TABLE;
import static org.ballerinalang.model.elements.PackageID.TRANSACTION;
import static org.ballerinalang.model.elements.PackageID.TYPEDESC;
import static org.ballerinalang.model.elements.PackageID.VALUE;
import static org.ballerinalang.model.elements.PackageID.XML;
import static org.ballerinalang.model.symbols.SymbolOrigin.BUILTIN;
import static org.ballerinalang.model.symbols.SymbolOrigin.SOURCE;
import static org.ballerinalang.model.symbols.SymbolOrigin.VIRTUAL;
import static org.ballerinalang.model.tree.NodeKind.IMPORT;
import static org.ballerinalang.util.diagnostic.DiagnosticErrorCode.DEFAULTABLE_PARAM_DEFINED_AFTER_INCLUDED_RECORD_PARAM;
import static org.ballerinalang.util.diagnostic.DiagnosticErrorCode.EXPECTED_RECORD_TYPE_AS_INCLUDED_PARAMETER;
import static org.ballerinalang.util.diagnostic.DiagnosticErrorCode.REDECLARED_SYMBOL;
import static org.ballerinalang.util.diagnostic.DiagnosticErrorCode.REQUIRED_PARAM_DEFINED_AFTER_DEFAULTABLE_PARAM;
import static org.ballerinalang.util.diagnostic.DiagnosticErrorCode.REQUIRED_PARAM_DEFINED_AFTER_INCLUDED_RECORD_PARAM;
import static org.wso2.ballerinalang.compiler.semantics.model.Scope.NOT_FOUND_ENTRY;

/**
 * @since 0.94
 */
public class SymbolEnter extends BLangNodeVisitor {

    private static final CompilerContext.Key<SymbolEnter> SYMBOL_ENTER_KEY =
            new CompilerContext.Key<>();

    private final PackageLoader pkgLoader;
    private final SymbolTable symTable;
    private final Names names;
    private final SymbolResolver symResolver;
    private final BLangDiagnosticLog dlog;
    private final Types types;
    private final SourceDirectory sourceDirectory;
    private List<BLangNode> unresolvedTypes;
    private List<BLangClassDefinition> unresolvedClasses;
    private HashSet<LocationData> unknownTypeRefs;
    private List<PackageID> importedPackages;
    private int typePrecedence;
    private final TypeParamAnalyzer typeParamAnalyzer;
    private BLangAnonymousModelHelper anonymousModelHelper;
    private BLangMissingNodesHelper missingNodesHelper;
    private PackageCache packageCache;
    private List<BLangNode> intersectionTypes;

    private SymbolEnv env;
    private final boolean projectAPIInitiatedCompilation;

    private static final String DEPRECATION_ANNOTATION = "deprecated";
    private static final String ANONYMOUS_RECORD_NAME = "anonymous-record";

    public static SymbolEnter getInstance(CompilerContext context) {
        SymbolEnter symbolEnter = context.get(SYMBOL_ENTER_KEY);
        if (symbolEnter == null) {
            symbolEnter = new SymbolEnter(context);
        }

        return symbolEnter;
    }

    public SymbolEnter(CompilerContext context) {
        context.put(SYMBOL_ENTER_KEY, this);

        this.pkgLoader = PackageLoader.getInstance(context);
        this.symTable = SymbolTable.getInstance(context);
        this.names = Names.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
        this.types = Types.getInstance(context);
        this.typeParamAnalyzer = TypeParamAnalyzer.getInstance(context);
        this.anonymousModelHelper = BLangAnonymousModelHelper.getInstance(context);
        this.sourceDirectory = context.get(SourceDirectory.class);
        this.importedPackages = new ArrayList<>();
        this.unknownTypeRefs = new HashSet<>();
        this.missingNodesHelper = BLangMissingNodesHelper.getInstance(context);
        this.packageCache = PackageCache.getInstance(context);
        this.intersectionTypes = new ArrayList<>();

        CompilerOptions options = CompilerOptions.getInstance(context);
        projectAPIInitiatedCompilation = Boolean.parseBoolean(
                options.get(CompilerOptionName.PROJECT_API_INITIATED_COMPILATION));
    }

    public BLangPackage definePackage(BLangPackage pkgNode) {
        dlog.setCurrentPackageId(pkgNode.packageID);
        populatePackageNode(pkgNode);
        defineNode(pkgNode, this.symTable.pkgEnvMap.get(symTable.langAnnotationModuleSymbol));
        return pkgNode;
    }

    public void defineNode(BLangNode node, SymbolEnv env) {
        SymbolEnv prevEnv = this.env;
        this.env = env;
        node.accept(this);
        this.env = prevEnv;
    }

    public BLangPackage defineTestablePackage(BLangTestablePackage pkgNode, SymbolEnv env) {
        populatePackageNode(pkgNode);
        defineNode(pkgNode, env);
        return pkgNode;
    }

    // Visitor methods

    @Override
    public void visit(BLangPackage pkgNode) {
        if (pkgNode.completedPhases.contains(CompilerPhase.DEFINE)) {
            return;
        }

        // Create PackageSymbol
        BPackageSymbol pkgSymbol;
        if (Symbols.isFlagOn(Flags.asMask(pkgNode.flagSet), Flags.TESTABLE)) {
            pkgSymbol = Symbols.createPackageSymbol(pkgNode.packageID, this.symTable, Flags.asMask(pkgNode.flagSet),
                                                    SOURCE);
        } else {
            pkgSymbol = Symbols.createPackageSymbol(pkgNode.packageID, this.symTable, SOURCE);
        }
        if (PackageID.isLangLibPackageID(pkgSymbol.pkgID)) {
            populateLangLibInSymTable(pkgSymbol);
        }

        if (pkgNode.moduleContextDataHolder != null) {
            pkgSymbol.exported = pkgNode.moduleContextDataHolder.isExported();
            pkgSymbol.descriptor = pkgNode.moduleContextDataHolder.descriptor();
        }

        pkgNode.symbol = pkgSymbol;
        SymbolEnv pkgEnv = SymbolEnv.createPkgEnv(pkgNode, pkgSymbol.scope, this.env);
        this.symTable.pkgEnvMap.put(pkgSymbol, pkgEnv);

        // Add the current package node's ID to the imported package list. This is used to identify cyclic module
        // imports.
        importedPackages.add(pkgNode.packageID);

        defineConstructs(pkgNode, pkgEnv);
        pkgNode.getTestablePkgs().forEach(testablePackage -> defineTestablePackage(testablePackage, pkgEnv));
        pkgNode.completedPhases.add(CompilerPhase.DEFINE);

        // After we have visited a package node, we need to remove it from the imports list.
        importedPackages.remove(pkgNode.packageID);
    }

    private void defineConstructs(BLangPackage pkgNode, SymbolEnv pkgEnv) {
        // visit the package node recursively and define all package level symbols.
        // And maintain a list of created package symbols.
        Map<String, ImportResolveHolder> importPkgHolder = new HashMap<>();
        pkgNode.imports.forEach(importNode -> {
            String qualifiedName = importNode.getQualifiedPackageName();
            if (importPkgHolder.containsKey(qualifiedName)) {
                importPkgHolder.get(qualifiedName).unresolved.add(importNode);
                return;
            }
            defineNode(importNode, pkgEnv);
            if (importNode.symbol != null) {
                importPkgHolder.put(qualifiedName, new ImportResolveHolder(importNode));
            }
        });

        for (ImportResolveHolder importHolder : importPkgHolder.values()) {
            BPackageSymbol pkgSymbol = importHolder.resolved.symbol; // get a copy of the package symbol, add
            // compilation unit info to it,

            for (BLangImportPackage unresolvedPkg : importHolder.unresolved) {
                BPackageSymbol importSymbol = importHolder.resolved.symbol;
                Name resolvedPkgAlias = names.fromIdNode(importHolder.resolved.alias);
                Name unresolvedPkgAlias = names.fromIdNode(unresolvedPkg.alias);

                // check if its the same import or has the same alias.
                if (!Names.IGNORE.equals(unresolvedPkgAlias) && unresolvedPkgAlias.equals(resolvedPkgAlias)
                    && importSymbol.compUnit.equals(names.fromIdNode(unresolvedPkg.compUnit))) {
                    if (isSameImport(unresolvedPkg, importSymbol)) {
                        dlog.error(unresolvedPkg.pos, DiagnosticErrorCode.REDECLARED_IMPORT_MODULE,
                                unresolvedPkg.getQualifiedPackageName());
                    } else {
                        dlog.error(unresolvedPkg.pos, DiagnosticErrorCode.REDECLARED_SYMBOL, unresolvedPkgAlias);
                    }
                    continue;
                }

                unresolvedPkg.symbol = pkgSymbol;
                // and define it in the current package scope
                BPackageSymbol symbol = dupPackageSymbolAndSetCompUnit(pkgSymbol,
                        names.fromIdNode(unresolvedPkg.compUnit));
                symbol.scope = pkgSymbol.scope;
                unresolvedPkg.symbol = symbol;
                pkgEnv.scope.define(unresolvedPkgAlias, symbol);
            }
        }
        initPredeclaredModules(symTable.predeclaredModules, pkgNode.compUnits, pkgEnv);
        // Define type definitions.
        this.typePrecedence = 0;

        // Treat constants and type definitions in the same manner, since constants can be used as
        // types. Also, there can be references between constant and type definitions in both ways.
        // Thus visit them according to the precedence.
        List<BLangNode> typeAndClassDefs = new ArrayList<>();
        pkgNode.constants.forEach(constant -> typeAndClassDefs.add(constant));
        pkgNode.typeDefinitions.forEach(typDef -> typeAndClassDefs.add(typDef));
        List<BLangClassDefinition> classDefinitions = getClassDefinitions(pkgNode.topLevelNodes);
        classDefinitions.forEach(classDefn -> typeAndClassDefs.add(classDefn));
        defineTypeNodes(typeAndClassDefs, pkgEnv);

        for (BLangVariable variable : pkgNode.globalVars) {
            if (variable.expr != null && variable.expr.getKind() == NodeKind.LAMBDA && variable.isDeclaredWithVar) {
                resolveAndSetFunctionTypeFromRHSLambda(variable, pkgEnv);
            }
        }

        // Enabled logging errors after type def visit.
        // TODO: Do this in a cleaner way
        pkgEnv.logErrors = true;

        // Sort type definitions with precedence, before defining their members.
        pkgNode.typeDefinitions.sort(getTypePrecedenceComparator());
        typeAndClassDefs.sort(getTypePrecedenceComparator());

        // Define type def fields (if any)
        defineFields(typeAndClassDefs, pkgEnv);

        // Calculate error intersections types.
        defineIntersectionTypes(pkgEnv);

        // Define error details.
        defineErrorDetails(pkgNode.typeDefinitions, pkgEnv);

        // Add distinct type information
        defineDistinctClassAndObjectDefinitions(typeAndClassDefs);

        // Define type def members (if any)
        defineMembers(typeAndClassDefs, pkgEnv);

        // Intersection type nodes need to look at the member fields of a structure too.
        // Once all the fields and members of other types are set revisit intersection type definitions to validate
        // them and set the fields and members of the relevant immutable type.
        validateIntersectionTypeDefinitions(pkgNode.typeDefinitions);
        defineUndefinedReadOnlyTypes(pkgNode.typeDefinitions, typeAndClassDefs, pkgEnv);

        // Define service and resource nodes.
        pkgNode.services.forEach(service -> defineNode(service, pkgEnv));

        // Define function nodes.
        pkgNode.functions.forEach(func -> defineNode(func, pkgEnv));

        // Define annotation nodes.
        pkgNode.annotations.forEach(annot -> defineNode(annot, pkgEnv));

        pkgNode.globalVars.forEach(var -> defineNode(var, pkgEnv));

        // Update globalVar for endpoints.
        for (BLangVariable var : pkgNode.globalVars) {
            if (var.getKind() == NodeKind.VARIABLE) {
                BVarSymbol varSymbol = var.symbol;
                if (varSymbol != null) {
                    BTypeSymbol tSymbol = varSymbol.type.tsymbol;
                    if (tSymbol != null && Symbols.isFlagOn(tSymbol.flags, Flags.CLIENT)) {
                        varSymbol.tag = SymTag.ENDPOINT;
                    }
                }
            }
        }
    }

    private void defineIntersectionTypes(SymbolEnv env) {
        for (BLangNode typeDescriptor : this.intersectionTypes) {
            defineNode(typeDescriptor, env);
        }
        this.intersectionTypes.clear();
    }


    private void populateSecondaryTypeIdSet(Set<BTypeIdSet.BTypeId> secondaryTypeIds, BErrorType typeOne) {
        secondaryTypeIds.addAll(typeOne.typeIdSet.primary);
        secondaryTypeIds.addAll(typeOne.typeIdSet.secondary);
    }

    private void defineErrorType(BErrorType errorType, SymbolEnv env) {
        SymbolEnv pkgEnv = symTable.pkgEnvMap.get(env.enclPkg.symbol);
        BTypeSymbol errorTSymbol = errorType.tsymbol;
        errorTSymbol.scope = new Scope(errorTSymbol);
        pkgEnv.scope.define(errorTSymbol.name, errorTSymbol);

        SymbolEnv prevEnv = this.env;
        this.env = pkgEnv;
        this.env = prevEnv;
    }

    private void defineDistinctClassAndObjectDefinitions(List<BLangNode> typDefs) {
        for (BLangNode node : typDefs) {
            if (node.getKind() == NodeKind.CLASS_DEFN) {
                populateDistinctTypeIdsFromIncludedTypeReferences((BLangClassDefinition) node);
            } else if (node.getKind() == NodeKind.TYPE_DEFINITION) {
                populateDistinctTypeIdsFromIncludedTypeReferences((BLangTypeDefinition) node);
            }
        }
    }

    private void populateDistinctTypeIdsFromIncludedTypeReferences(BLangTypeDefinition typeDefinition) {
        if (typeDefinition.typeNode.getKind() != NodeKind.OBJECT_TYPE) {
            return;
        }

        BLangObjectTypeNode objectTypeNode = (BLangObjectTypeNode) typeDefinition.typeNode;
        BTypeIdSet typeIdSet = ((BObjectType) objectTypeNode.type).typeIdSet;

        for (BLangType typeRef : objectTypeNode.typeRefs) {
            if (typeRef.type.tag != TypeTags.OBJECT) {
                continue;
            }
            BObjectType refType = (BObjectType) typeRef.type;

            if (!refType.typeIdSet.primary.isEmpty()) {
                typeIdSet.primary.addAll(refType.typeIdSet.primary);
            }
            if (!refType.typeIdSet.secondary.isEmpty()) {
                typeIdSet.secondary.addAll(refType.typeIdSet.secondary);
            }
        }
    }

    private void populateDistinctTypeIdsFromIncludedTypeReferences(BLangClassDefinition typeDef) {
        BLangClassDefinition classDefinition = typeDef;
        BTypeIdSet typeIdSet = ((BObjectType) classDefinition.type).typeIdSet;

        for (BLangType typeRef : classDefinition.typeRefs) {
            if (typeRef.type.tag != TypeTags.OBJECT) {
                continue;
            }
            BObjectType refType = (BObjectType) typeRef.type;

            if (!refType.typeIdSet.primary.isEmpty()) {
                typeIdSet.primary.addAll(refType.typeIdSet.primary);
            }
            if (!refType.typeIdSet.secondary.isEmpty()) {
                typeIdSet.secondary.addAll(refType.typeIdSet.secondary);
            }
        }
    }

    private Comparator<BLangNode> getTypePrecedenceComparator() {
        return new Comparator<BLangNode>() {
            @Override
            public int compare(BLangNode l, BLangNode r) {
                if (l instanceof OrderedNode && r instanceof OrderedNode) {
                    return ((OrderedNode) l).getPrecedence() - ((OrderedNode) r).getPrecedence();
                }
                return 0;
            }
        };
    }

    private void defineMembersOfClassDef(SymbolEnv pkgEnv, BLangClassDefinition classDefinition) {
        BObjectType objectType = (BObjectType) classDefinition.symbol.type;

        if (objectType.mutableType != null) {
            // If this is an object type definition defined for an immutable type.
            // We skip defining methods here since they would either be defined already, or would be defined
            // later.
            return;
        }

        SymbolEnv objMethodsEnv =
                SymbolEnv.createClassMethodsEnv(classDefinition, (BObjectTypeSymbol) classDefinition.symbol, pkgEnv);

        // Define the functions defined within the object
        defineClassInitFunction(classDefinition, objMethodsEnv);
        classDefinition.functions.forEach(f -> {
            f.flagSet.add(Flag.FINAL); // Method can't be changed
            f.setReceiver(ASTBuilderUtil.createReceiver(classDefinition.pos, objectType));
            defineNode(f, objMethodsEnv);
        });

        defineIncludedMethods(classDefinition, objMethodsEnv, false);
    }

    private void defineIncludedMethods(BLangClassDefinition classDefinition, SymbolEnv objMethodsEnv,
                                       boolean defineReadOnlyInclusionsOnly) {
        Set<String> includedFunctionNames = new HashSet<>();

        if (defineReadOnlyInclusionsOnly) {
            for (BAttachedFunction function : ((BObjectTypeSymbol) classDefinition.type.tsymbol).referencedFunctions) {
                includedFunctionNames.add(function.funcName.value);
            }
        }

        // Add the attached functions of the referenced types to this object.
        // Here it is assumed that all the attached functions of the referred type are
        // resolved by the time we reach here. It is achieved by ordering the typeDefs
        // according to the precedence.
        for (BLangType typeRef : classDefinition.typeRefs) {
            BType type = typeRef.type;
            if (type == null || type == symTable.semanticError) {
                return;
            }

            if (type.tag == TypeTags.INTERSECTION) {
                if (!defineReadOnlyInclusionsOnly) {
                    // Will be defined once all the readonly type's methods are defined.
                    continue;
                }

                type = ((BIntersectionType) type).effectiveType;
            } else {
                if (defineReadOnlyInclusionsOnly) {
                    if (!isImmutable((BObjectType) type)) {
                        continue;
                    }
                } else if (isImmutable((BObjectType) type)) {
                    continue;
                }
            }

            List<BAttachedFunction> functions = ((BObjectTypeSymbol) type.tsymbol).attachedFuncs;
            for (BAttachedFunction function : functions) {
                defineReferencedFunction(classDefinition.pos, classDefinition.flagSet, objMethodsEnv,
                        typeRef, function, includedFunctionNames, classDefinition.symbol, classDefinition.functions,
                        classDefinition.internal);
            }
        }
    }

    private void defineReferencedClassFields(BLangClassDefinition classDefinition, SymbolEnv typeDefEnv,
                                             BObjectType objType, boolean defineReadOnlyInclusionsOnly) {
        Set<BSymbol> referencedTypes = new HashSet<>();
        List<BLangType> invalidTypeRefs = new ArrayList<>();
        // Get the inherited fields from the type references

        Map<String, BLangSimpleVariable> fieldNames = new HashMap<>();
        for (BLangSimpleVariable fieldVariable : classDefinition.fields) {
            fieldNames.put(fieldVariable.name.value, fieldVariable);
        }

        List<BLangSimpleVariable> referencedFields = new ArrayList<>();

        for (BLangType typeRef : classDefinition.typeRefs) {
            BType referredType = symResolver.resolveTypeNode(typeRef, typeDefEnv);
            if (referredType == symTable.semanticError) {
                continue;
            }

            int tag = classDefinition.type.tag;
            if (tag == TypeTags.OBJECT) {
                if (isInvalidIncludedTypeInClass(referredType)) {
                    if (!defineReadOnlyInclusionsOnly) {
                        dlog.error(typeRef.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPE_REFERENCE, typeRef);
                    }
                    invalidTypeRefs.add(typeRef);
                    continue;
                }

                BObjectType objectType = null;

                if (referredType.tag == TypeTags.INTERSECTION) {
                    if (!defineReadOnlyInclusionsOnly) {
                        // Will be defined once all the readonly type's fields are defined.
                        continue;
                    }
                } else {
                    objectType = (BObjectType) referredType;

                    if (defineReadOnlyInclusionsOnly) {
                        if (!isImmutable(objectType)) {
                            continue;
                        }
                    } else if (isImmutable(objectType)) {
                        continue;
                    }
                }
            } else if (defineReadOnlyInclusionsOnly) {
                continue;
            }

            // Check for duplicate type references
            if (!referencedTypes.add(referredType.tsymbol)) {
                dlog.error(typeRef.pos, DiagnosticErrorCode.REDECLARED_TYPE_REFERENCE, typeRef);
                continue;
            }

            BType effectiveIncludedType = referredType;

            if (tag == TypeTags.OBJECT) {
                BObjectType objectType;

                if (referredType.tag == TypeTags.INTERSECTION) {
                    effectiveIncludedType = objectType = (BObjectType) ((BIntersectionType) referredType).effectiveType;
                } else {
                    objectType = (BObjectType) referredType;
                }

                if (classDefinition.type.tsymbol.owner != referredType.tsymbol.owner) {
                    boolean errored = false;
                    for (BField field : objectType.fields.values()) {
                        if (!Symbols.isPublic(field.symbol)) {
                            dlog.error(typeRef.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPE_REFERENCE_NON_PUBLIC_MEMBERS,
                                       typeRef);
                            invalidTypeRefs.add(typeRef);
                            errored = true;
                            break;
                        }
                    }

                    if (errored) {
                        continue;
                    }

                    for (BAttachedFunction func : ((BObjectTypeSymbol) objectType.tsymbol).attachedFuncs) {
                        if (!Symbols.isPublic(func.symbol)) {
                            dlog.error(typeRef.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPE_REFERENCE_NON_PUBLIC_MEMBERS,
                                       typeRef);
                            invalidTypeRefs.add(typeRef);
                            errored = true;
                            break;
                        }
                    }

                    if (errored) {
                        continue;
                    }
                }
            }

            // Here it is assumed that all the fields of the referenced types are resolved
            // by the time we reach here. It is achieved by ordering the typeDefs according
            // to the precedence.
            // Default values of fields are not inherited.
            for (BField field : ((BStructureType) effectiveIncludedType).fields.values()) {
                if (fieldNames.containsKey(field.name.value)) {
                    BLangSimpleVariable existingVariable = fieldNames.get(field.name.value);
                    if ((existingVariable.flagSet.contains(Flag.PUBLIC) !=
                            Symbols.isFlagOn(field.symbol.flags, Flags.PUBLIC)) ||
                            (existingVariable.flagSet.contains(Flag.PRIVATE) !=
                                    Symbols.isFlagOn(field.symbol.flags, Flags.PRIVATE))) {
                        dlog.error(existingVariable.pos,
                                DiagnosticErrorCode.MISMATCHED_VISIBILITY_QUALIFIERS_IN_OBJECT_FIELD,
                                existingVariable.name.value);
                    }
                    if (types.isAssignable(existingVariable.type, field.type)) {
                        continue;
                    }
                }

                BLangSimpleVariable var = ASTBuilderUtil.createVariable(typeRef.pos, field.name.value, field.type);
                var.flagSet = field.symbol.getFlags();
                referencedFields.add(var);
            }
        }
        classDefinition.typeRefs.removeAll(invalidTypeRefs);

        for (BLangSimpleVariable field : referencedFields) {
            defineNode(field, typeDefEnv);
            if (field.symbol.type == symTable.semanticError) {
                continue;
            }
            objType.fields.put(field.name.value, new BField(names.fromIdNode(field.name), field.pos, field.symbol));
        }

        classDefinition.referencedFields.addAll(referencedFields);
    }

    private List<BLangClassDefinition> getClassDefinitions(List<TopLevelNode> topLevelNodes) {
        List<BLangClassDefinition> classDefinitions = new ArrayList<>();
        for (TopLevelNode topLevelNode : topLevelNodes) {
            if (topLevelNode.getKind() == NodeKind.CLASS_DEFN) {
                classDefinitions.add((BLangClassDefinition) topLevelNode);
            }
        }
        return classDefinitions;
    }

    @Override
    public void visit(BLangClassDefinition classDefinition) {
        EnumSet<Flag> flags = EnumSet.copyOf(classDefinition.flagSet);
        boolean isPublicType = flags.contains(Flag.PUBLIC);
        Name className = names.fromIdNode(classDefinition.name);

        BClassSymbol tSymbol = Symbols.createClassSymbol(Flags.asMask(flags), className, env.enclPkg.symbol.pkgID, null,
                                                         env.scope.owner, classDefinition.name.pos,
                                                         getOrigin(className, flags), classDefinition.isServiceDecl);
        tSymbol.scope = new Scope(tSymbol);
        tSymbol.markdownDocumentation = getMarkdownDocAttachment(classDefinition.markdownDocumentationAttachment);


        long typeFlags = 0;

        if (flags.contains(Flag.READONLY)) {
            typeFlags |= Flags.READONLY;
        }

        if (flags.contains(Flag.ISOLATED)) {
            typeFlags |= Flags.ISOLATED;
        }

        if (flags.contains(Flag.SERVICE)) {
            typeFlags |= Flags.SERVICE;
        }

        if (flags.contains(Flag.OBJECT_CTOR)) {
            typeFlags |= Flags.OBJECT_CTOR;
        }

        BObjectType objectType = new BObjectType(tSymbol, typeFlags);

        if (flags.contains(Flag.DISTINCT)) {
            objectType.typeIdSet = BTypeIdSet.from(env.enclPkg.symbol.pkgID, classDefinition.name.value, isPublicType);
        }

        if (flags.contains(Flag.CLIENT)) {
            objectType.flags |= Flags.CLIENT;
        }

        tSymbol.type = objectType;
        classDefinition.type = objectType;
        classDefinition.symbol = tSymbol;

        if (isDeprecated(classDefinition.annAttachments)) {
            tSymbol.flags |= Flags.DEPRECATED;
        }

        // For each referenced type, check whether the types are already resolved.
        // If not, then that type should get a higher precedence.
        for (BLangType typeRef : classDefinition.typeRefs) {
            BType referencedType = symResolver.resolveTypeNode(typeRef, env);
            if (referencedType == symTable.noType && !this.unresolvedTypes.contains(classDefinition)) {
                this.unresolvedTypes.add(classDefinition);
                return;
            }
            objectType.typeInclusions.add(referencedType);
        }

        classDefinition.setPrecedence(this.typePrecedence++);
        if (symResolver.checkForUniqueSymbol(classDefinition.pos, env, tSymbol)) {
            env.scope.define(tSymbol.name, tSymbol);
        }
        env.scope.define(tSymbol.name, tSymbol);
    }

    public void visit(BLangAnnotation annotationNode) {
        Name annotName = names.fromIdNode(annotationNode.name);
        BAnnotationSymbol annotationSymbol = Symbols.createAnnotationSymbol(Flags.asMask(annotationNode.flagSet),
                                                                            annotationNode.getAttachPoints(),
                                                                            annotName, env.enclPkg.symbol.pkgID, null,
                                                                            env.scope.owner, annotationNode.name.pos,
                                                                            getOrigin(annotName));
        annotationSymbol.markdownDocumentation =
                getMarkdownDocAttachment(annotationNode.markdownDocumentationAttachment);
        if (isDeprecated(annotationNode.annAttachments)) {
            annotationSymbol.flags |= Flags.DEPRECATED;
        }
        annotationSymbol.type = new BAnnotationType(annotationSymbol);
        annotationNode.symbol = annotationSymbol;
        defineSymbol(annotationNode.name.pos, annotationSymbol);
        SymbolEnv annotationEnv = SymbolEnv.createAnnotationEnv(annotationNode, annotationSymbol.scope, env);
        BLangType annotTypeNode = annotationNode.typeNode;
        if (annotTypeNode != null) {
            BType type = this.symResolver.resolveTypeNode(annotTypeNode, annotationEnv);
            annotationSymbol.attachedType = type.tsymbol;
            if (!isValidAnnotationType(type)) {
                dlog.error(annotTypeNode.pos, DiagnosticErrorCode.ANNOTATION_INVALID_TYPE, type);
            }

//            if (annotationNode.flagSet.contains(Flag.CONSTANT) && !type.isAnydata()) {
//                dlog.error(annotTypeNode.pos, DiagnosticErrorCode.ANNOTATION_INVALID_CONST_TYPE, type);
//            }
        }

        if (!annotationNode.flagSet.contains(Flag.CONSTANT) &&
                annotationNode.getAttachPoints().stream().anyMatch(attachPoint -> attachPoint.source)) {
            dlog.error(annotationNode.pos, DiagnosticErrorCode.ANNOTATION_REQUIRES_CONST);
        }
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    @Override
    public void visit(BLangImportPackage importPkgNode) {
        Name pkgAlias = names.fromIdNode(importPkgNode.alias);
        if (!Names.IGNORE.equals(pkgAlias)) {
            BSymbol importSymbol =
                    symResolver.resolvePrefixSymbol(env, pkgAlias, names.fromIdNode(importPkgNode.compUnit));
            if (importSymbol != symTable.notFoundSymbol) {
                if (isSameImport(importPkgNode, (BPackageSymbol) importSymbol)) {
                    dlog.error(importPkgNode.pos, DiagnosticErrorCode.REDECLARED_IMPORT_MODULE,
                            importPkgNode.getQualifiedPackageName());
                } else {
                    dlog.error(importPkgNode.pos, DiagnosticErrorCode.REDECLARED_SYMBOL, pkgAlias);
                }
                return;
            }
        }

        // TODO Clean this code up. Can we move the this to BLangPackageBuilder class
        // Create import package symbol
        Name orgName;
        Name version;
        PackageID enclPackageID = env.enclPkg.packageID;
        // The pattern of the import statement is 'import [org-name /] module-name [version sem-ver]'
        // Three cases should be considered here.
        // 1. import org-name/module-name version
        // 2. import org-name/module-name
        //      2a. same project
        //      2b. different project
        // 3. import module-name
        if (!isNullOrEmpty(importPkgNode.orgName.value)) {
            orgName = names.fromIdNode(importPkgNode.orgName);
            if (!isNullOrEmpty(importPkgNode.version.value)) {
                version = names.fromIdNode(importPkgNode.version);
            } else {
                // TODO We are removing the version in the import declaration anyway
                if (projectAPIInitiatedCompilation) {
                    version = Names.EMPTY;
                } else {
                    String pkgName = importPkgNode.getPackageName().stream()
                            .map(id -> id.value)
                            .collect(Collectors.joining("."));
                    if (this.sourceDirectory.getSourcePackageNames().contains(pkgName)
                            && orgName.value.equals(enclPackageID.orgName.value)) {
                        version = enclPackageID.version;
                    } else {
                        version = Names.EMPTY;
                    }
                }
            }
        } else {
            orgName = enclPackageID.orgName;
            version = (Names.DEFAULT_VERSION.equals(enclPackageID.version)) ? Names.EMPTY : enclPackageID.version;
        }

        List<Name> nameComps = importPkgNode.pkgNameComps.stream()
                .map(identifier -> names.fromIdNode(identifier))
                .collect(Collectors.toList());

        PackageID pkgId = new PackageID(orgName, nameComps, version);

        // Un-exported modules not inside current package is not allowed to import.
        BPackageSymbol bPackageSymbol = this.packageCache.getSymbol(pkgId);
        if (bPackageSymbol != null && this.env.enclPkg.moduleContextDataHolder != null) {
            boolean isCurrentPackageModuleImport =
                this.env.enclPkg.moduleContextDataHolder.descriptor().org() == bPackageSymbol.descriptor.org()
                    && this.env.enclPkg.moduleContextDataHolder.descriptor().packageName() ==
                        bPackageSymbol.descriptor.packageName();
            if (!isCurrentPackageModuleImport && !bPackageSymbol.exported) {
                dlog.error(importPkgNode.pos, DiagnosticErrorCode.MODULE_NOT_FOUND,
                           bPackageSymbol.toString() + " is not exported");
                           return;
            }
        }

        // Built-in Annotation module is not allowed to import.
        if (pkgId.equals(PackageID.ANNOTATIONS) || pkgId.equals(PackageID.INTERNAL) || pkgId.equals(PackageID.QUERY)) {
            // Only peer lang.* modules able to see these two modules.
            // Spec allows to annotation model to be imported, but implementation not support this.
            if (!(enclPackageID.orgName.equals(Names.BALLERINA_ORG)
                    && enclPackageID.name.value.startsWith(Names.LANG.value))) {
                dlog.error(importPkgNode.pos, DiagnosticErrorCode.MODULE_NOT_FOUND,
                        importPkgNode.getQualifiedPackageName());
                return;
            }
        }

        // Detect cyclic module dependencies. This will not detect cycles which starts with the entry package because
        // entry package has a version. So we check import cycles which starts with the entry package in next step.
        if (importedPackages.contains(pkgId)) {
            int index = importedPackages.indexOf(pkgId);
            // Generate the import cycle.
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = index; i < importedPackages.size(); i++) {
                stringBuilder.append(importedPackages.get(i).toString()).append(" -> ");
            }
            // Append the current package to complete the cycle.
            stringBuilder.append(pkgId);
            dlog.error(importPkgNode.pos, DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED, stringBuilder.toString());
            return;
        }

        boolean samePkg = false;
        // Get the entry package.
        PackageID entryPackage = importedPackages.get(0);
        if (entryPackage.isUnnamed == pkgId.isUnnamed) {
            samePkg = (!entryPackage.isUnnamed) || (entryPackage.sourceFileName.equals(pkgId.sourceFileName));
        }
        // Check whether the package which we have encountered is the same as the entry package. We don't need to
        // check the version here because we cannot import two different versions of the same package at the moment.
        if (samePkg && entryPackage.orgName.equals(pkgId.orgName) && entryPackage.name.equals(pkgId.name)) {
            StringBuilder stringBuilder = new StringBuilder();
            String entryPackageString = importedPackages.get(0).toString();
            // We need to remove the package.
            int packageIndex = entryPackageString.indexOf(":");
            if (packageIndex != -1) {
                entryPackageString = entryPackageString.substring(0, packageIndex);
            }
            // Generate the import cycle.
            stringBuilder.append(entryPackageString).append(" -> ");
            for (int i = 1; i < importedPackages.size(); i++) {
                stringBuilder.append(importedPackages.get(i).toString()).append(" -> ");
            }
            stringBuilder.append(pkgId);
            dlog.error(importPkgNode.pos, DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED, stringBuilder.toString());
            return;
        }

        BPackageSymbol pkgSymbol;
        if (projectAPIInitiatedCompilation) {
            pkgSymbol = packageCache.getSymbol(pkgId);
        } else {
            pkgSymbol = pkgLoader.loadPackageSymbol(pkgId, enclPackageID, this.env.enclPkg.repos);
        }

        if (pkgSymbol == null) {
            dlog.error(importPkgNode.pos, DiagnosticErrorCode.MODULE_NOT_FOUND,
                    importPkgNode.getQualifiedPackageName());
            return;
        }

        List<BPackageSymbol> imports = ((BPackageSymbol) this.env.scope.owner).imports;
        if (!imports.contains(pkgSymbol)) {
            imports.add(pkgSymbol);
        }

        // get a copy of the package symbol, add compilation unit info to it,
        // and define it in the current package scope
        BPackageSymbol symbol = dupPackageSymbolAndSetCompUnit(pkgSymbol, names.fromIdNode(importPkgNode.compUnit));
        symbol.scope = pkgSymbol.scope;
        importPkgNode.symbol = symbol;
        this.env.scope.define(pkgAlias, symbol);
    }

    public void initPredeclaredModules(Map<Name, BPackageSymbol> predeclaredModules,
                                       List<BLangCompilationUnit> compUnits, SymbolEnv env) {
        SymbolEnv prevEnv = this.env;
        this.env = env;
        for (Name alias : predeclaredModules.keySet()) {
            int index = 0;
            ScopeEntry entry = this.env.scope.lookup(alias);
            if (entry == NOT_FOUND_ENTRY && !compUnits.isEmpty()) {
                this.env.scope.define(alias, dupPackageSymbolAndSetCompUnit(predeclaredModules.get(alias),
                        new Name(compUnits.get(index++).name)));
                entry = this.env.scope.lookup(alias);
            }
            for (int i = index; i < compUnits.size(); i++) {
                boolean isUndefinedModule = true;
                String compUnitName = compUnits.get(i).name;
                if (((BPackageSymbol) entry.symbol).compUnit.value.equals(compUnitName)) {
                    isUndefinedModule = false;
                }
                while (entry.next != NOT_FOUND_ENTRY) {
                    if (((BPackageSymbol) entry.next.symbol).compUnit.value.equals(compUnitName)) {
                        isUndefinedModule = false;
                        break;
                    }
                    entry = entry.next;
                }
                if (isUndefinedModule) {
                    entry.next = new ScopeEntry(dupPackageSymbolAndSetCompUnit(predeclaredModules.get(alias),
                            new Name(compUnitName)), NOT_FOUND_ENTRY);
                }
            }
        }
        this.env = prevEnv;
    }

    @Override
    public void visit(BLangXMLNS xmlnsNode) {
        String nsURI;
        if (xmlnsNode.namespaceURI.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
            BLangSimpleVarRef varRef = (BLangSimpleVarRef) xmlnsNode.namespaceURI;
            if (missingNodesHelper.isMissingNode(varRef.variableName.value)) {
                nsURI = "";
            } else {
                // TODO: handle const-ref (#24911)
                nsURI = "";
            }
        } else {
            nsURI = (String) ((BLangLiteral) xmlnsNode.namespaceURI).value;
            if (!nullOrEmpty(xmlnsNode.prefix.value) && nsURI.isEmpty()) {
                dlog.error(xmlnsNode.pos, DiagnosticErrorCode.INVALID_NAMESPACE_DECLARATION, xmlnsNode.prefix);
            }
        }

        // set the prefix of the default namespace
        if (xmlnsNode.prefix.value == null) {
            xmlnsNode.prefix.value = XMLConstants.DEFAULT_NS_PREFIX;
        }

        Name prefix = names.fromIdNode(xmlnsNode.prefix);
        Location nsSymbolPos = prefix.value.isEmpty() ? xmlnsNode.pos : xmlnsNode.prefix.pos;
        BXMLNSSymbol xmlnsSymbol = Symbols.createXMLNSSymbol(prefix, nsURI, env.enclPkg.symbol.pkgID, env.scope.owner,
                                                             nsSymbolPos, getOrigin(prefix));
        xmlnsNode.symbol = xmlnsSymbol;

        // First check for package-imports with the same alias.
        // Here we do not check for owner equality, since package import is always at the package
        // level, but the namespace declaration can be at any level.
        BSymbol foundSym = symResolver.lookupSymbolInPrefixSpace(env, xmlnsSymbol.name);
        if ((foundSym.tag & SymTag.PACKAGE) != SymTag.PACKAGE) {
            foundSym = symTable.notFoundSymbol;
        }
        if (foundSym != symTable.notFoundSymbol) {
            dlog.error(xmlnsNode.pos, DiagnosticErrorCode.REDECLARED_SYMBOL, xmlnsSymbol.name);
            return;
        }

        // Define it in the enclosing scope. Here we check for the owner equality,
        // to support overriding of namespace declarations defined at package level.
        defineSymbol(xmlnsNode.prefix.pos, xmlnsSymbol);
    }

    private boolean nullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public void visit(BLangXMLNSStatement xmlnsStmtNode) {
        defineNode(xmlnsStmtNode.xmlnsDecl, env);
    }

    private void defineTypeNodes(List<BLangNode> typeDefs, SymbolEnv env) {
        if (typeDefs.isEmpty()) {
            return;
        }

        this.unresolvedTypes = new ArrayList<>();
        for (BLangNode typeDef : typeDefs) {
            if (isErrorIntersectionType(typeDef, env)) {
                populateUndefinedErrorIntersection((BLangTypeDefinition) typeDef, env);
                continue;
            }

            defineNode(typeDef, env);
        }

        if (typeDefs.size() <= unresolvedTypes.size()) {
            // This situation can occur due to either a cyclic dependency or at least one of member types in type
            // definition node cannot be resolved. So we iterate through each node recursively looking for cyclic
            // dependencies or undefined types in type node.


            for (BLangNode unresolvedType : unresolvedTypes) {
                Stack<String> references = new Stack<>();
                var unresolvedKind = unresolvedType.getKind();
                if (unresolvedKind == NodeKind.TYPE_DEFINITION || unresolvedKind == NodeKind.CONSTANT) {
                    TypeDefinition def = (TypeDefinition) unresolvedType;
                    // We need to keep track of all visited types to print cyclic dependency.
                    references.push(def.getName().getValue());
                    checkErrors(env, unresolvedType, (BLangNode) def.getTypeNode(), references, false);
                } else if (unresolvedType.getKind() == NodeKind.CLASS_DEFN) {
                    BLangClassDefinition classDefinition = (BLangClassDefinition) unresolvedType;
                    references.push(classDefinition.getName().getValue());
                    checkErrors(env, unresolvedType, classDefinition, references, true);
                }
            }

            unresolvedTypes.forEach(type -> defineNode(type, env));
            return;
        }
        defineTypeNodes(unresolvedTypes, env);
    }

    private void populateUndefinedErrorIntersection(BLangTypeDefinition typeDef, SymbolEnv env) {
        BErrorType intersectionErrorType = types.createErrorType(null, Flags.PUBLIC, env);
        intersectionErrorType.tsymbol.name = names.fromString(typeDef.name.value);
        defineErrorType(intersectionErrorType, env);

        this.intersectionTypes.add(typeDef);
    }

    private boolean isErrorIntersectionType(BLangNode typeDef, SymbolEnv env) {
        boolean isIntersectionType = typeDef.getKind() == NodeKind.TYPE_DEFINITION
                && ((BLangTypeDefinition) typeDef).typeNode.getKind() == NodeKind.INTERSECTION_TYPE_NODE;
        if (!isIntersectionType) {
            return false;
        }

        BLangIntersectionTypeNode intersectionTypeNode =
                (BLangIntersectionTypeNode) ((BLangTypeDefinition) typeDef).typeNode;

        for (BLangType type : intersectionTypeNode.constituentTypeNodes) {
            BType bType = symResolver.resolveTypeNode(type, env);
            if (bType.tag == TypeTags.ERROR) {
                return true;
            }
        }
        return false;
    }

    private void checkErrors(SymbolEnv env, BLangNode unresolvedType, BLangNode currentTypeOrClassNode,
                             Stack<String> visitedNodes,
                             boolean fromStructuredType) {
        // Check errors in the type definition.
        List<BLangType> memberTypeNodes;
        switch (currentTypeOrClassNode.getKind()) {
            case ARRAY_TYPE:
                checkErrors(env, unresolvedType, ((BLangArrayType) currentTypeOrClassNode).elemtype, visitedNodes,
                        true);
                break;
            case UNION_TYPE_NODE:
                // If the current type node is a union type node, we need to check all member nodes.
                memberTypeNodes = ((BLangUnionTypeNode) currentTypeOrClassNode).memberTypeNodes;
                // Recursively check all members.
                for (BLangType memberTypeNode : memberTypeNodes) {
                    checkErrors(env, unresolvedType, memberTypeNode, visitedNodes, fromStructuredType);
                    if (((BLangTypeDefinition) unresolvedType).hasCyclicReference) {
                        break;
                    }
                }
                break;
            case INTERSECTION_TYPE_NODE:
                memberTypeNodes = ((BLangIntersectionTypeNode) currentTypeOrClassNode).constituentTypeNodes;
                for (BLangType memberTypeNode : memberTypeNodes) {
                    checkErrors(env, unresolvedType, memberTypeNode, visitedNodes, fromStructuredType);
                }
                break;
            case TUPLE_TYPE_NODE:
                memberTypeNodes = ((BLangTupleTypeNode) currentTypeOrClassNode).memberTypeNodes;
                for (BLangType memberTypeNode : memberTypeNodes) {
                    checkErrors(env, unresolvedType, memberTypeNode, visitedNodes, true);
                }
                break;
            case CONSTRAINED_TYPE:
                checkErrors(env, unresolvedType, ((BLangConstrainedType) currentTypeOrClassNode).constraint,
                        visitedNodes,
                        true);
                break;
            case TABLE_TYPE:
                checkErrors(env, unresolvedType, ((BLangTableTypeNode) currentTypeOrClassNode).constraint, visitedNodes,
                        true);
                break;
            case USER_DEFINED_TYPE:
                checkErrorsOfUserDefinedType(env, unresolvedType, (BLangUserDefinedType) currentTypeOrClassNode,
                        visitedNodes, fromStructuredType);
                break;
            case BUILT_IN_REF_TYPE:
                // Eg - `xml`. This is not needed to be checked because no types are available in the `xml`.
            case FINITE_TYPE_NODE:
            case VALUE_TYPE:
            case ERROR_TYPE:
                // Do nothing.
                break;
            case FUNCTION_TYPE:
                BLangFunctionTypeNode functionTypeNode = (BLangFunctionTypeNode) currentTypeOrClassNode;
                functionTypeNode.params.forEach(p -> checkErrors(env, unresolvedType, p.typeNode, visitedNodes,
                        fromStructuredType));
                if (functionTypeNode.restParam != null) {
                    checkErrors(env, unresolvedType, functionTypeNode.restParam.typeNode, visitedNodes,
                            fromStructuredType);
                }
                if (functionTypeNode.returnTypeNode != null) {
                    checkErrors(env, unresolvedType, functionTypeNode.returnTypeNode, visitedNodes, fromStructuredType);
                }
                break;
            case RECORD_TYPE:
                for (TypeNode typeNode : ((BLangRecordTypeNode) currentTypeOrClassNode).getTypeReferences()) {
                    checkErrors(env, unresolvedType, (BLangType) typeNode, visitedNodes, true);
                }
                break;
            case OBJECT_TYPE:
                for (TypeNode typeNode : ((BLangObjectTypeNode) currentTypeOrClassNode).getTypeReferences()) {
                    checkErrors(env, unresolvedType, (BLangType) typeNode, visitedNodes, true);
                }
                break;
            case CLASS_DEFN:
                for (TypeNode typeNode : ((BLangClassDefinition) currentTypeOrClassNode).typeRefs) {
                    checkErrors(env, unresolvedType, (BLangType) typeNode, visitedNodes, true);
                }
                break;
            default:
                throw new RuntimeException("unhandled type kind: " + currentTypeOrClassNode.getKind());
        }
    }

    private void checkErrorsOfUserDefinedType(SymbolEnv env, BLangNode unresolvedType,
                                              BLangUserDefinedType currentTypeOrClassNode,
                                              Stack<String> visitedNodes, boolean fromStructuredType) {
        String currentTypeNodeName = currentTypeOrClassNode.typeName.value;
        // Skip all types defined as anonymous types.
        if (currentTypeNodeName.startsWith("$")) {
            return;
        }
        String unresolvedTypeNodeName = getTypeOrClassName(unresolvedType);
        boolean sameTypeNode = unresolvedTypeNodeName.equals(currentTypeNodeName);
        boolean isVisited = visitedNodes.contains(currentTypeNodeName);
        boolean typeDef = unresolvedType.getKind() == NodeKind.TYPE_DEFINITION;

        if (sameTypeNode || isVisited) {
            if (typeDef) {
                BLangTypeDefinition typeDefinition = (BLangTypeDefinition) unresolvedType;
                if (fromStructuredType && typeDefinition.getTypeNode().getKind() == NodeKind.UNION_TYPE_NODE) {
                    // Valid cyclic dependency
                    // type A int|map<A>;
                    typeDefinition.hasCyclicReference = true;
                    return;
                }
            }

            // Only type definitions with unions are allowed to have cyclic reference
            if (isVisited) {
                // Invalid dependency detected. But in here, all the types in the list might not
                // be necessary for the cyclic dependency error message.
                //
                // Eg - A -> B -> C -> B // Last B is what we are currently checking
                //
                // In such case, we create a new list with relevant type names.
                int i = visitedNodes.indexOf(currentTypeNodeName);
                List<String> dependencyList = new ArrayList<>(visitedNodes.size() - i);
                for (; i < visitedNodes.size(); i++) {
                    dependencyList.add(visitedNodes.get(i));
                }
                if (!sameTypeNode && dependencyList.size() == 1
                        && dependencyList.get(0).equals(currentTypeNodeName)) {
                    // Check to support valid scenarios such as the following
                    // type A int\A[];
                    // type B A;
                    // @typeparam type B A;
                    return;
                }
                // Add the `currentTypeNodeName` to complete the cycle.
                dependencyList.add(currentTypeNodeName);
                dlog.error(unresolvedType.getPosition(), DiagnosticErrorCode.CYCLIC_TYPE_REFERENCE, dependencyList);
            } else {
                visitedNodes.push(currentTypeNodeName);
                dlog.error(unresolvedType.getPosition(), DiagnosticErrorCode.CYCLIC_TYPE_REFERENCE, visitedNodes);
                visitedNodes.remove(currentTypeNodeName);
            }
        } else {
            // Check whether the current type node is in the unresolved list. If it is in the list, we need to
            // check it recursively.
            List<BLangNode> typeDefinitions = unresolvedTypes.stream()
                    .filter(node -> getTypeOrClassName(node).equals(currentTypeNodeName)).collect(Collectors.toList());

            if (typeDefinitions.isEmpty()) {
                BType referredType = symResolver.resolveTypeNode(currentTypeOrClassNode, env);
                if (referredType.tag == TypeTags.RECORD || referredType.tag == TypeTags.OBJECT ||
                        referredType.tag == TypeTags.FINITE) {
                    // we are referring an fully or partially defined type from another cyclic type eg: record, class
                    return;
                }

                // If a type is declared, it should either get defined successfully or added to the unresolved
                // types list. If a type is not in either one of them, that means it is an undefined type.
                LocationData locationData = new LocationData(
                        currentTypeNodeName, currentTypeOrClassNode.pos.lineRange().startLine().line(),
                        currentTypeOrClassNode.pos.lineRange().startLine().offset());
                if (unknownTypeRefs.add(locationData)) {
                    dlog.error(currentTypeOrClassNode.pos, DiagnosticErrorCode.UNKNOWN_TYPE, currentTypeNodeName);
                }
            } else {
                for (BLangNode typeDefinition : typeDefinitions) {
                    if (typeDefinition.getKind() == NodeKind.TYPE_DEFINITION) {
                        BLangTypeDefinition langTypeDefinition = (BLangTypeDefinition) typeDefinition;
                        String typeName = langTypeDefinition.getName().getValue();
                        // Add the node name to the list.
                        visitedNodes.push(typeName);
                        // Recursively check for errors.
                        checkErrors(env, unresolvedType, langTypeDefinition.getTypeNode(), visitedNodes,
                                fromStructuredType);
                        // We need to remove the added type node here since we have finished checking errors.
                        visitedNodes.pop();
                    } else {
                        BLangClassDefinition classDefinition = (BLangClassDefinition) typeDefinition;
                        visitedNodes.push(classDefinition.getName().getValue());
                        checkErrors(env, unresolvedType, classDefinition, visitedNodes, fromStructuredType);
                        visitedNodes.pop();
                    }
                }
            }
        }
    }

    private String getTypeOrClassName(BLangNode node) {
        if (node.getKind() == NodeKind.TYPE_DEFINITION || node.getKind() == NodeKind.CONSTANT) {
            return ((TypeDefinition) node).getName().getValue();
        } else  {
            return ((BLangClassDefinition) node).getName().getValue();
        }
    }

    public boolean isUnknownTypeRef(BLangUserDefinedType bLangUserDefinedType) {
        var startLine = bLangUserDefinedType.pos.lineRange().startLine();
        LocationData locationData = new LocationData(bLangUserDefinedType.typeName.value, startLine.line(),
                startLine.offset());
        return unknownTypeRefs.contains(locationData);
    }

    @Override
    public void visit(BLangTypeDefinition typeDefinition) {
        BType definedType;
        if (typeDefinition.hasCyclicReference) {
            definedType = getCyclicDefinedType(typeDefinition, env);
        } else {
            definedType = symResolver.resolveTypeNode(typeDefinition.typeNode, env);
        }

        if (definedType == symTable.semanticError) {
            // TODO : Fix this properly. issue #21242

            invalidateAlreadyDefinedErrorType(typeDefinition);
            return;
        }
        if (definedType == symTable.noType) {
            // This is to prevent concurrent modification exception.
            if (!this.unresolvedTypes.contains(typeDefinition)) {
                this.unresolvedTypes.add(typeDefinition);
            }
            return;
        }

        boolean isErrorIntersection = isErrorIntersection(definedType);
        if (isErrorIntersection) {
            populateSymbolNamesForErrorIntersection(definedType, typeDefinition);
            populateUndefinedErrorIntersection(definedType, typeDefinition, env);
        }

        // Check for any circular type references
        if (typeDefinition.typeNode.getKind() == NodeKind.OBJECT_TYPE ||
                typeDefinition.typeNode.getKind() == NodeKind.RECORD_TYPE) {
            BLangStructureTypeNode structureTypeNode = (BLangStructureTypeNode) typeDefinition.typeNode;
            // For each referenced type, check whether the types are already resolved.
            // If not, then that type should get a higher precedence.
            for (BLangType typeRef : structureTypeNode.typeRefs) {
                BType referencedType = symResolver.resolveTypeNode(typeRef, env);
                if (referencedType == symTable.noType) {
                    if (!this.unresolvedTypes.contains(typeDefinition)) {
                        this.unresolvedTypes.add(typeDefinition);
                        return;
                    }
                }
            }
        }

        // check for unresolved fields. This record may be referencing another record
        if (typeDefinition.typeNode.getKind() == NodeKind.RECORD_TYPE) {
            BLangStructureTypeNode structureTypeNode = (BLangStructureTypeNode) typeDefinition.typeNode;
            for (BLangSimpleVariable variable : structureTypeNode.fields) {
                BType referencedType = symResolver.resolveTypeNode(variable.typeNode, env);
                if (referencedType == symTable.noType) {
                    if (!this.unresolvedTypes.contains(typeDefinition)) {
                        this.unresolvedTypes.add(typeDefinition);
                        return;
                    }
                }
            }
        }

        if (typeDefinition.typeNode.getKind() == NodeKind.FUNCTION_TYPE && definedType.tsymbol == null) {
            definedType.tsymbol = Symbols.createTypeSymbol(SymTag.FUNCTION_TYPE, Flags.asMask(typeDefinition.flagSet),
                                                           Names.EMPTY, env.enclPkg.symbol.pkgID, definedType,
                                                           env.scope.owner, typeDefinition.pos, SOURCE);
        }

        if (typeDefinition.flagSet.contains(Flag.ENUM)) {
            definedType.tsymbol = createEnumSymbol(typeDefinition, definedType);
        }

        typeDefinition.setPrecedence(this.typePrecedence++);
        BTypeSymbol typeDefSymbol;
        if (definedType.tsymbol.name != Names.EMPTY) {
            typeDefSymbol = definedType.tsymbol.createLabelSymbol();
        } else {
            typeDefSymbol = definedType.tsymbol;
        }
        typeDefSymbol.markdownDocumentation = getMarkdownDocAttachment(typeDefinition.markdownDocumentationAttachment);
        typeDefSymbol.name = names.fromIdNode(typeDefinition.getName());
        typeDefSymbol.pkgID = env.enclPkg.packageID;
        typeDefSymbol.pos = typeDefinition.name.pos;
        typeDefSymbol.origin = getOrigin(typeDefSymbol.name);

        boolean distinctFlagPresent = isDistinctFlagPresent(typeDefinition);

        if (distinctFlagPresent) {
            if (definedType.getKind() == TypeKind.ERROR) {
                BErrorType distinctType = getDistinctErrorType(typeDefinition, (BErrorType) definedType, typeDefSymbol);
                typeDefinition.typeNode.type = distinctType;
                definedType = distinctType;
            } else if (definedType.getKind() == TypeKind.INTERSECTION
                    && ((BIntersectionType) definedType).effectiveType.getKind() == TypeKind.ERROR) {
                populateTypeIds((BErrorType) ((BIntersectionType) definedType).effectiveType,
                                (BLangIntersectionTypeNode) typeDefinition.typeNode, typeDefinition.name.value);
            } else if (definedType.getKind() == TypeKind.OBJECT) {
                BObjectType distinctType = getDistinctObjectType(typeDefinition, (BObjectType) definedType,
                                                                 typeDefSymbol);
                typeDefinition.typeNode.type = distinctType;
                definedType = distinctType;
            } else if (definedType.getKind() == TypeKind.UNION) {
                validateUnionForDistinctType((BUnionType) definedType, typeDefinition.pos);
            } else {
                dlog.error(typeDefinition.pos, DiagnosticErrorCode.DISTINCT_TYPING_ONLY_SUPPORT_OBJECTS_AND_ERRORS);
            }
        }

        typeDefSymbol.flags |= Flags.asMask(typeDefinition.flagSet);
        // Reset public flag when set on a non public type.
        typeDefSymbol.flags &= getPublicFlagResetingMask(typeDefinition.flagSet, typeDefinition.typeNode);
        if (isDeprecated(typeDefinition.annAttachments)) {
            typeDefSymbol.flags |= Flags.DEPRECATED;
        }

        // Reset origin for anonymous types
        if (Symbols.isFlagOn(typeDefSymbol.flags, Flags.ANONYMOUS)) {
            typeDefSymbol.origin = VIRTUAL;
        }

        if (typeDefinition.annAttachments.stream()
                .anyMatch(attachment -> attachment.annotationName.value.equals(Names.ANNOTATION_TYPE_PARAM.value))) {
            // TODO : Clean this. Not a nice way to handle this.
            //  TypeParam is built-in annotation, and limited only within lang.* modules.
            if (PackageID.isLangLibPackageID(this.env.enclPkg.packageID)) {
                typeDefSymbol.type = typeParamAnalyzer.createTypeParam(typeDefSymbol.type, typeDefSymbol.name);
                typeDefSymbol.flags |= Flags.TYPE_PARAM;
                if (typeDefinition.typeNode.getKind() == NodeKind.ERROR_TYPE) {
                    typeDefSymbol.isLabel = false;
                }
            } else {
                dlog.error(typeDefinition.pos, DiagnosticErrorCode.TYPE_PARAM_OUTSIDE_LANG_MODULE);
            }
        }
        definedType.flags |= typeDefSymbol.flags;

        typeDefinition.symbol = typeDefSymbol;
        if (typeDefinition.hasCyclicReference) {
            // Workaround for https://github.com/ballerina-platform/ballerina-lang/issues/29742
            typeDefinition.type.tsymbol = typeDefSymbol;
        } else {
            boolean isLanglibModule = PackageID.isLangLibPackageID(this.env.enclPkg.packageID);
            if (isLanglibModule) {
                handleLangLibTypes(typeDefinition);
                return;
            }
            if (!isErrorIntersection) { // We have already defined for IntersectionTtypeDef
                defineSymbol(typeDefinition.name.pos, typeDefSymbol);
            }
        }
    }

    private void invalidateAlreadyDefinedErrorType(BLangTypeDefinition typeDefinition) {
        // We need to invalidate the already defined type as we don't have a way to undefine it.
        BSymbol alreadyDefinedTypeSymbol =
                                symResolver.lookupSymbolInMainSpace(env, names.fromString(typeDefinition.name.value));
        if (alreadyDefinedTypeSymbol.type.tag == TypeTags.ERROR) {
            alreadyDefinedTypeSymbol.type = symTable.errorType;
        }
    }

    private void populateTypeIds(BErrorType effectiveType, BLangIntersectionTypeNode typeNode, String name) {
        Set<BTypeIdSet.BTypeId> secondaryTypeIds = new HashSet<>();
        for (BLangType constituentType : typeNode.constituentTypeNodes) {
            BType type = symResolver.resolveTypeNode(constituentType, env);
            if (type.getKind() == TypeKind.ERROR) {
                populateSecondaryTypeIdSet(secondaryTypeIds, (BErrorType) type);
            }
        }
        effectiveType.typeIdSet = BTypeIdSet.from(env.enclPkg.packageID, name, true, secondaryTypeIds);
    }

    private void populateUndefinedErrorIntersection(BType definedType, BLangTypeDefinition typeDefinition,
                                                    SymbolEnv env) {

        BIntersectionType intersectionType = (BIntersectionType) definedType;
        BTypeSymbol alreadyDefinedErrorTypeSymbol =
                (BTypeSymbol) symResolver.lookupSymbolInMainSpace(env,
                                                                  names.fromString(typeDefinition.name.value));
        BErrorType alreadyDefinedErrorType = (BErrorType) alreadyDefinedErrorTypeSymbol.type;
        BErrorType errorType = (BErrorType) intersectionType.effectiveType;

        alreadyDefinedErrorType.typeIdSet = errorType.typeIdSet;
        alreadyDefinedErrorType.detailType = errorType.detailType;
        alreadyDefinedErrorType.flags = errorType.flags;
        alreadyDefinedErrorType.name = errorType.name;
        intersectionType.effectiveType = alreadyDefinedErrorType;
    }

    private void populateSymbolNamesForErrorIntersection(BType definedType, BLangTypeDefinition typeDefinition) {
        String typeDefName = typeDefinition.name.value;
        definedType.tsymbol.name = names.fromString(typeDefName);

        BErrorType effectiveErrorType = (BErrorType) ((BIntersectionType) definedType).effectiveType;
        effectiveErrorType.tsymbol.name = names.fromString(typeDefName);
    }

    private boolean isErrorIntersection(BType definedType) {
        if (definedType.tag == TypeTags.INTERSECTION) {
            BIntersectionType intersectionType = (BIntersectionType) definedType;
            return intersectionType.effectiveType.tag == TypeTags.ERROR;
        }

        return false;
    }

    private BEnumSymbol createEnumSymbol(BLangTypeDefinition typeDefinition, BType definedType) {
        List<BConstantSymbol> enumMembers = new ArrayList<>();

        List<BLangType> members = ((BLangUnionTypeNode) typeDefinition.typeNode).memberTypeNodes;
        for (BLangType member : members) {
            enumMembers.add((BConstantSymbol) ((BLangUserDefinedType) member).symbol);
        }

        return new BEnumSymbol(enumMembers, Flags.asMask(typeDefinition.flagSet), Names.EMPTY, env.enclPkg.symbol.pkgID,
                               definedType, env.scope.owner, typeDefinition.pos, SOURCE);
    }

    private BObjectType getDistinctObjectType(BLangTypeDefinition typeDefinition, BObjectType definedType,
                                              BTypeSymbol typeDefSymbol) {
        BObjectType definedObjType = definedType;
        // Create a new type for distinct type definition such as `type FooErr distinct BarErr;`
        // `typeDefSymbol` is different to `definedObjType.tsymbol` in a type definition statement that use
        // already defined type as the base type.
        if (definedObjType.tsymbol != typeDefSymbol) {
            BObjectType objType = new BObjectType(typeDefSymbol);
            typeDefSymbol.type = objType;
            definedObjType = objType;
        }
        boolean isPublicType = typeDefinition.flagSet.contains(Flag.PUBLIC);
        definedObjType.typeIdSet = calculateTypeIdSet(typeDefinition, isPublicType, definedType.typeIdSet);
        return definedObjType;
    }

    private BType getCyclicDefinedType(BLangTypeDefinition typeDef, SymbolEnv env) {
        BUnionType unionType = BUnionType.create(null, new LinkedHashSet<>());
        unionType.isCyclic = true;
        Name typeDefName = names.fromIdNode(typeDef.name);

        BTypeSymbol typeDefSymbol = Symbols.createTypeSymbol(SymTag.UNION_TYPE, Flags.asMask(typeDef.flagSet),
                typeDefName, env.enclPkg.symbol.pkgID, unionType, env.scope.owner,
                typeDef.name.pos, SOURCE);

        typeDef.symbol = typeDefSymbol;
        unionType.tsymbol = typeDefSymbol;

        // We define the unionType in the main scope here
        if (PackageID.isLangLibPackageID(this.env.enclPkg.packageID)) {
            typeDefSymbol.origin = BUILTIN;
            handleLangLibTypes(typeDef);
        } else {
            defineSymbol(typeDef.name.pos, typeDefSymbol);
        }

        // resolvedUnionWrapper is not the union we need. Resolver tries to create a union for us but only manages to
        // resolve members as they are defined as user defined types. Since we define the symbol user defined types
        // gets resolved. We also expect becaue we are calling this API we dont have to call
        // `markParameterizedType(unionType, memberTypes);` again for resolved members
        BType resolvedUnionWrapper = symResolver.resolveTypeNode(typeDef.typeNode, env);
        // Transform all members from union wrapper to defined union type
        if (resolvedUnionWrapper.tag == TypeTags.UNION) {
            BUnionType definedUnionType = (BUnionType) resolvedUnionWrapper;
            definedUnionType.tsymbol = typeDefSymbol;
            unionType.flags |= typeDefSymbol.flags;
            for (BType member : definedUnionType.getMemberTypes()) {
                unionType.add(member);
            }
        }
        typeDef.typeNode.type = unionType;
        typeDef.typeNode.type.tsymbol.type = unionType;
        typeDef.symbol.type = unionType;
        typeDef.type = unionType;
        return unionType;
    }

    private void validateUnionForDistinctType(BUnionType definedType, Location pos) {
        Set<BType> memberTypes = definedType.getMemberTypes();
        TypeKind firstTypeKind = null;
        for (BType type : memberTypes) {
            TypeKind typeKind = type.getKind();
            if (firstTypeKind == null && (typeKind == TypeKind.ERROR || typeKind == TypeKind.OBJECT)) {
                firstTypeKind = typeKind;

            }
            if (typeKind != firstTypeKind) {
                dlog.error(pos, DiagnosticErrorCode.DISTINCT_TYPING_ONLY_SUPPORT_OBJECTS_AND_ERRORS);
            }
        }
    }

    private BErrorType getDistinctErrorType(BLangTypeDefinition typeDefinition, BErrorType definedType,
                                            BTypeSymbol typeDefSymbol) {
        BErrorType definedErrorType = definedType;
        // Create a new type for distinct type definition such as `type FooErr distinct BarErr;`
        // `typeDefSymbol` is different to `definedErrorType.tsymbol` in a type definition statement that use
        // already defined type as the base type.
        if (definedErrorType.tsymbol != typeDefSymbol) {
            BErrorType bErrorType = new BErrorType(typeDefSymbol);
            bErrorType.detailType = definedErrorType.detailType;
            typeDefSymbol.type = bErrorType;
            definedErrorType = bErrorType;
        }
        boolean isPublicType = typeDefinition.flagSet.contains(Flag.PUBLIC);
        definedErrorType.typeIdSet = calculateTypeIdSet(typeDefinition, isPublicType, definedType.typeIdSet);
        return definedErrorType;
    }

    private BTypeIdSet calculateTypeIdSet(BLangTypeDefinition typeDefinition, boolean isPublicType,
                                          BTypeIdSet secondary) {
        String name = typeDefinition.flagSet.contains(Flag.ANONYMOUS)
                ? anonymousModelHelper.getNextDistinctErrorId(env.enclPkg.packageID)
                : typeDefinition.getName().value;

        return BTypeIdSet.from(env.enclPkg.packageID, name, isPublicType, secondary);
    }

    private boolean isDistinctFlagPresent(BLangTypeDefinition typeDefinition) {
        return typeDefinition.typeNode.flagSet.contains(Flag.DISTINCT);
    }

    private void handleLangLibTypes(BLangTypeDefinition typeDefinition) {

        // As per spec 2020R3 built-in types are limited only within lang.* modules.
        for (BLangAnnotationAttachment attachment : typeDefinition.annAttachments) {
            if (attachment.annotationName.value.equals(Names.ANNOTATION_TYPE_PARAM.value)) {
                BTypeSymbol typeDefSymbol = typeDefinition.symbol;
                typeDefSymbol.type = typeParamAnalyzer.createTypeParam(typeDefSymbol.type, typeDefSymbol.name);
                typeDefSymbol.flags |= Flags.TYPE_PARAM;
                break;
            } else if (attachment.annotationName.value.equals(Names.ANNOTATION_BUILTIN_SUBTYPE.value)) {
                // Type is pre-defined in symbol Table.
                BType type = symTable.getLangLibSubType(typeDefinition.name.value);
                typeDefinition.symbol = type.tsymbol;
                typeDefinition.type = type;
                typeDefinition.typeNode.type = type;
                typeDefinition.isBuiltinTypeDef = true;
                break;
            }
            throw new IllegalStateException("Not supported annotation attachment at:" + attachment.pos);
        }
        defineSymbol(typeDefinition.name.pos, typeDefinition.symbol);
    }

    // If this type is defined to a public type or this is a anonymous type, return int with all bits set to 1,
    // so that we can bitwise and it with any flag and the original flag will not change.
    // If the type is not a public type then return a mask where public flag is set to zero and all others are set
    // to 1 so that we can perform bitwise and operation to remove the public flag from given flag.
    private long getPublicFlagResetingMask(Set<Flag> flagSet, BLangType typeNode) {
        boolean isAnonType =
                typeNode instanceof BLangStructureTypeNode && ((BLangStructureTypeNode) typeNode).isAnonymous;
        if (flagSet.contains(Flag.PUBLIC) || isAnonType) {
            return Long.MAX_VALUE;
        } else {
            return ~Flags.PUBLIC;
        }
    }

    @Override
    public void visit(BLangWorker workerNode) {
        BInvokableSymbol workerSymbol = Symbols.createWorkerSymbol(Flags.asMask(workerNode.flagSet),
                                                                   names.fromIdNode(workerNode.name),
                                                                   env.enclPkg.symbol.pkgID, null, env.scope.owner,
                                                                   workerNode.pos, SOURCE);
        workerSymbol.markdownDocumentation = getMarkdownDocAttachment(workerNode.markdownDocumentationAttachment);
        workerNode.symbol = workerSymbol;
        defineSymbolWithCurrentEnvOwner(workerNode.pos, workerSymbol);
    }

    @Override
    public void visit(BLangService serviceNode) {
        defineNode(serviceNode.serviceVariable, env);

        Name generatedServiceName = names.fromString("service$" + serviceNode.serviceClass.symbol.name.value);
        BType type = serviceNode.serviceClass.typeRefs.isEmpty() ? null : serviceNode.serviceClass.typeRefs.get(0).type;
        BServiceSymbol serviceSymbol = new BServiceSymbol((BClassSymbol) serviceNode.serviceClass.symbol,
                                                          Flags.asMask(serviceNode.flagSet), generatedServiceName,
                                                          env.enclPkg.symbol.pkgID, type, env.enclPkg.symbol,
                                                          serviceNode.pos, SOURCE);
        serviceNode.symbol = serviceSymbol;

        if (!serviceNode.absoluteResourcePath.isEmpty()) {
            if ("/".equals(serviceNode.absoluteResourcePath.get(0).getValue())) {
                serviceSymbol.setAbsResourcePath(Collections.emptyList());
            } else {
                List<String> list = new ArrayList<>();
                for (IdentifierNode identifierNode : serviceNode.absoluteResourcePath) {
                    list.add(identifierNode.getValue());
                }
                serviceSymbol.setAbsResourcePath(list);
            }
        }

        if (serviceNode.serviceNameLiteral != null) {
            serviceSymbol.setAttachPointStringLiteral(serviceNode.serviceNameLiteral.value.toString());
        }

        env.scope.define(serviceSymbol.name, serviceSymbol);
    }

    @Override
    public void visit(BLangResourceFunction funcNode) {
        boolean validAttachedFunc = validateFuncReceiver(funcNode);

        if (PackageID.isLangLibPackageID(env.enclPkg.symbol.pkgID)) {
            funcNode.flagSet.add(Flag.LANG_LIB);
        }

        BInvokableSymbol funcSymbol = Symbols.createFunctionSymbol(Flags.asMask(funcNode.flagSet),
                getFuncSymbolName(funcNode),
                env.enclPkg.symbol.pkgID, null, env.scope.owner,
                funcNode.hasBody(), funcNode.name.pos, SOURCE);
        funcSymbol.source = funcNode.pos.lineRange().filePath();
        funcSymbol.markdownDocumentation = getMarkdownDocAttachment(funcNode.markdownDocumentationAttachment);
        SymbolEnv invokableEnv = SymbolEnv.createFunctionEnv(funcNode, funcSymbol.scope, env);
        defineInvokableSymbol(funcNode, funcSymbol, invokableEnv);
        funcNode.type = funcSymbol.type;

        if (isDeprecated(funcNode.annAttachments)) {
            funcSymbol.flags |= Flags.DEPRECATED;
        }
        // Define function receiver if any.
        if (funcNode.receiver != null) {
            defineAttachedFunctions(funcNode, funcSymbol, invokableEnv, validAttachedFunc);
        }
    }

    @Override
    public void visit(BLangFunction funcNode) {
        boolean validAttachedFunc = validateFuncReceiver(funcNode);
        boolean remoteFlagSetOnNode = Symbols.isFlagOn(Flags.asMask(funcNode.flagSet), Flags.REMOTE);

        if (!funcNode.attachedFunction && Symbols.isFlagOn(Flags.asMask(funcNode.flagSet), Flags.PRIVATE)) {
            dlog.error(funcNode.pos, DiagnosticErrorCode.PRIVATE_FUNCTION_VISIBILITY, funcNode.name);
        }

        if (funcNode.receiver == null && !funcNode.attachedFunction && remoteFlagSetOnNode) {
            dlog.error(funcNode.pos, DiagnosticErrorCode.REMOTE_IN_NON_OBJECT_FUNCTION, funcNode.name.value);
        }

        if (PackageID.isLangLibPackageID(env.enclPkg.symbol.pkgID)) {
            funcNode.flagSet.add(Flag.LANG_LIB);
        }

        Location symbolPos = funcNode.flagSet.contains(Flag.LAMBDA) ?
                                                        symTable.builtinPos : funcNode.name.pos;
        BInvokableSymbol funcSymbol = Symbols.createFunctionSymbol(Flags.asMask(funcNode.flagSet),
                                                                   getFuncSymbolName(funcNode),
                                                                   env.enclPkg.symbol.pkgID, null, env.scope.owner,
                                                                   funcNode.hasBody(), symbolPos,
                                                                   getOrigin(funcNode.name.value));
        funcSymbol.source = funcNode.pos.lineRange().filePath();
        funcSymbol.markdownDocumentation = getMarkdownDocAttachment(funcNode.markdownDocumentationAttachment);
        SymbolEnv invokableEnv = SymbolEnv.createFunctionEnv(funcNode, funcSymbol.scope, env);
        defineInvokableSymbol(funcNode, funcSymbol, invokableEnv);
        funcNode.type = funcSymbol.type;

        // Reset origin if it's the generated function node for a lambda
        if (Symbols.isFlagOn(funcSymbol.flags, Flags.LAMBDA)) {
            funcSymbol.origin = VIRTUAL;
        }

        if (isDeprecated(funcNode.annAttachments)) {
            funcSymbol.flags |= Flags.DEPRECATED;
        }
        // Define function receiver if any.
        if (funcNode.receiver != null) {
            defineAttachedFunctions(funcNode, funcSymbol, invokableEnv, validAttachedFunc);
        }
    }

    private boolean isDeprecated(List<BLangAnnotationAttachment> annAttachments) {
        for (BLangAnnotationAttachment annotationAttachment : annAttachments) {
            if (annotationAttachment.annotationName.getValue().equals(DEPRECATION_ANNOTATION)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visit(BLangResource resourceNode) {
    }

    @Override
    public void visit(BLangConstant constant) {
        BType staticType;
        if (constant.typeNode != null) {
            staticType = symResolver.resolveTypeNode(constant.typeNode, env);
            if (staticType == symTable.noType) {
                constant.symbol = getConstantSymbol(constant);
                // This is to prevent concurrent modification exception.
                if (!this.unresolvedTypes.contains(constant)) {
                    this.unresolvedTypes.add(constant);
                }
                return;
            }
        } else {
            staticType = symTable.semanticError;
        }
        BConstantSymbol constantSymbol = getConstantSymbol(constant);
        constant.symbol = constantSymbol;

        NodeKind nodeKind = constant.expr.getKind();
        if (nodeKind == NodeKind.LITERAL || nodeKind == NodeKind.NUMERIC_LITERAL) {
            if (constant.typeNode != null) {
                if (types.isValidLiteral((BLangLiteral) constant.expr, staticType)) {
                    // A literal type constant is defined with correct type.
                    // Update the type of the finiteType node to the static type.
                    // This is done to make the type inferring work.
                    // eg: const decimal d = 5.0;
                    BLangFiniteTypeNode finiteType = (BLangFiniteTypeNode) constant.associatedTypeDefinition.typeNode;
                    BLangExpression valueSpaceExpr = finiteType.valueSpace.iterator().next();
                    valueSpaceExpr.type = staticType;
                    defineNode(constant.associatedTypeDefinition, env);

                    constantSymbol.type = constant.associatedTypeDefinition.symbol.type;
                    constantSymbol.literalType = staticType;
                } else {
                    // A literal type constant is defined with some incorrect type. Set the original
                    // types and continue the flow and let it fail at semantic analyzer.
                    defineNode(constant.associatedTypeDefinition, env);
                    constantSymbol.type = staticType;
                    constantSymbol.literalType = constant.expr.type;
                }
            } else {
                // A literal type constant is defined without the type.
                // Then the type of the symbol is the finite type.
                defineNode(constant.associatedTypeDefinition, env);
                constantSymbol.type = constant.associatedTypeDefinition.symbol.type;
                constantSymbol.literalType = constant.expr.type;
            }
        } else if (constant.typeNode != null) {
            constantSymbol.type = constantSymbol.literalType = staticType;
        }

        constantSymbol.markdownDocumentation = getMarkdownDocAttachment(constant.markdownDocumentationAttachment);
        if (isDeprecated(constant.annAttachments)) {
            constantSymbol.flags |= Flags.DEPRECATED;
        }
        // Add the symbol to the enclosing scope.
        if (!symResolver.checkForUniqueSymbol(constant.name.pos, env, constantSymbol)) {
            return;
        }

        if (constant.symbol.name == Names.IGNORE) {
            // Avoid symbol definition for constants with name '_'
            return;
        }
        // Add the symbol to the enclosing scope.
        env.scope.define(constantSymbol.name, constantSymbol);
    }

    private BConstantSymbol getConstantSymbol(BLangConstant constant) {
        // Create a new constant symbol.
        Name name = names.fromIdNode(constant.name);
        PackageID pkgID = env.enclPkg.symbol.pkgID;
        return new BConstantSymbol(Flags.asMask(constant.flagSet), name, pkgID, symTable.semanticError, symTable.noType,
                                   env.scope.owner, constant.name.pos, getOrigin(name));
    }

    @Override
    public void visit(BLangSimpleVariable varNode) {
        // assign the type to var type node
        if (varNode.type == null) {
            if (varNode.typeNode != null) {
                varNode.type = symResolver.resolveTypeNode(varNode.typeNode, env);
            } else {
                varNode.type = symTable.noType;
            }
        }

        Name varName = names.fromIdNode(varNode.name);
        if (varName == Names.EMPTY || varName == Names.IGNORE) {
            // This is a variable created for a return type
            // e.g. function foo() (int);
            return;
        }

        BVarSymbol varSymbol = defineVarSymbol(varNode.name.pos, varNode.flagSet, varNode.type, varName, env,
                                               varNode.internal);
        if (isDeprecated(varNode.annAttachments)) {
            varSymbol.flags |= Flags.DEPRECATED;
        }
        varSymbol.markdownDocumentation = getMarkdownDocAttachment(varNode.markdownDocumentationAttachment);
        varNode.symbol = varSymbol;
        if (varNode.symbol.type.tsymbol != null && Symbols.isFlagOn(varNode.symbol.type.tsymbol.flags, Flags.CLIENT)) {
            varSymbol.tag = SymTag.ENDPOINT;
        }

        if (varSymbol.type.tag == TypeTags.FUTURE && ((BFutureType) varSymbol.type).workerDerivative) {
            Iterator<BLangLambdaFunction> lambdaFunctions = env.enclPkg.lambdaFunctions.iterator();
            while (lambdaFunctions.hasNext()) {
                BLangLambdaFunction lambdaFunction = lambdaFunctions.next();
                // let's inject future symbol to all the lambdas
                // last lambda needs to be skipped to avoid self reference
                // lambda's form others functions also need to be skiped
                BLangInvokableNode enclInvokable = lambdaFunction.capturedClosureEnv.enclInvokable;
                if (lambdaFunctions.hasNext() && enclInvokable != null && varSymbol.owner == enclInvokable.symbol) {
                    lambdaFunction.capturedClosureEnv.scope.define(varSymbol.name, varSymbol);
                }
            }
        }

        if (varSymbol.type.tag == TypeTags.INVOKABLE) {
            BInvokableSymbol symbol = (BInvokableSymbol) varSymbol;
            BInvokableTypeSymbol tsymbol = (BInvokableTypeSymbol) symbol.type.tsymbol;
            symbol.params = tsymbol.params;
            symbol.restParam = tsymbol.restParam;
            symbol.retType = tsymbol.returnType;
        }

        if ((env.scope.owner.tag & SymTag.RECORD) != SymTag.RECORD && !varNode.flagSet.contains(Flag.NEVER_ALLOWED) &&
                types.isNeverTypeOrStructureTypeWithARequiredNeverMember(varSymbol.type)) {
            // check if the variable is defined as a 'never' type or equivalent to 'never'
            // (except inside a record type or iterative use (followed by in) in typed binding pattern)
            // if so, log an error
            if (varNode.flagSet.contains(Flag.REQUIRED_PARAM) || varNode.flagSet.contains(Flag.DEFAULTABLE_PARAM)) {
                dlog.error(varNode.pos, DiagnosticErrorCode.NEVER_TYPE_NOT_ALLOWED_FOR_REQUIRED_DEFAULTABLE_PARAMS);
            } else {
                if ((env.scope.owner.tag & SymTag.OBJECT) == SymTag.OBJECT) {
                    dlog.error(varNode.pos, DiagnosticErrorCode.NEVER_TYPED_OBJECT_FIELD_NOT_ALLOWED);
                } else {
                    dlog.error(varNode.pos, DiagnosticErrorCode.NEVER_TYPED_VAR_DEF_NOT_ALLOWED);
                }
            }
        }
    }

    @Override
    public void visit(BLangTupleVariable varNode) {
        if (varNode.isDeclaredWithVar) {
            varNode.symbol = defineVarSymbol(varNode.pos, varNode.flagSet, symTable.noType,
                    names.fromString(anonymousModelHelper.getNextTupleVarKey(env.enclPkg.packageID)), env,
                    varNode.internal);
            // Symbol enter with type other
            List<BLangVariable> memberVariables = new ArrayList<>(varNode.memberVariables);
            if (varNode.restVariable != null) {
                memberVariables.add(varNode.restVariable);
            }
            for (int i = 0; i < memberVariables.size(); i++) {
                BLangVariable memberVar = memberVariables.get(i);
                memberVar.isDeclaredWithVar = true;
                defineNode(memberVar, env);
            }
            return;
        }
        if (varNode.type == null) {
            varNode.type = symResolver.resolveTypeNode(varNode.typeNode, env);
        }
        // To support variable forward referencing we need to symbol enter each tuple member with type at SymbolEnter.
        if (!(checkTypeAndVarCountConsistency(varNode, env))) {
            varNode.type = symTable.semanticError;
            return;
        }
    }

    boolean checkTypeAndVarCountConsistency(BLangTupleVariable var, SymbolEnv env) {
        if (var.symbol == null) {
            Name varName = names.fromString(anonymousModelHelper.getNextTupleVarKey(env.enclPkg.packageID));
            var.symbol = defineVarSymbol(var.pos, var.flagSet, var.type, varName, env, var.internal);
        }
        
        return checkTypeAndVarCountConsistency(var, null, env);
    }

    boolean checkTypeAndVarCountConsistency(BLangTupleVariable varNode, BTupleType tupleTypeNode,
                                                    SymbolEnv env) {

        if (tupleTypeNode == null) {
        /*
          This switch block will resolve the tuple type of the tuple variable.
          For example consider the following - [int, string]|[boolean, float] [a, b] = foo();
          Since the varNode type is a union, the types of 'a' and 'b' will be resolved as follows:
          Type of 'a' will be (int | boolean) while the type of 'b' will be (string | float).
          Consider anydata (a, b) = foo();
          Here, the type of 'a'and type of 'b' will be both anydata.
         */
            switch (varNode.type.tag) {
                case TypeTags.UNION:
                    Set<BType> unionType = types.expandAndGetMemberTypesRecursive(varNode.type);
                    List<BType> possibleTypes = new ArrayList<>();
                    for (BType type : unionType) {
                        if (!(TypeTags.TUPLE == type.tag &&
                                checkMemVarCountMatchWithMemTypeCount(varNode, (BTupleType) type)) &&
                        TypeTags.ANY != type.tag && TypeTags.ANYDATA != type.tag &&
                                (TypeTags.ARRAY != type.tag || ((BArrayType) type).state == BArrayState.OPEN)) {
                            continue;
                        }
                        possibleTypes.add(type);
                    }
                    if (possibleTypes.isEmpty()) {
                        // handle var count mismatch in foreach declared with `var`
                        if (varNode.isDeclaredWithVar) {
                            dlog.error(varNode.pos, DiagnosticErrorCode.INVALID_LIST_BINDING_PATTERN);
                            return false;
                        }
                        dlog.error(varNode.pos, DiagnosticErrorCode.INVALID_LIST_BINDING_PATTERN_DECL, varNode.type);
                        return false;
                    }

                    if (possibleTypes.size() > 1) {
                        List<BType> memberTupleTypes = new ArrayList<>();
                        for (int i = 0; i < varNode.memberVariables.size(); i++) {
                            LinkedHashSet<BType> memberTypes = new LinkedHashSet<>();
                            for (BType possibleType : possibleTypes) {
                                if (possibleType.tag == TypeTags.TUPLE) {
                                    memberTypes.add(((BTupleType) possibleType).tupleTypes.get(i));
                                } else if (possibleType.tag == TypeTags.ARRAY) {
                                    memberTypes.add(((BArrayType) possibleType).eType);
                                } else {
                                    memberTupleTypes.add(varNode.type);
                                }
                            }

                            if (memberTypes.size() > 1) {
                                memberTupleTypes.add(BUnionType.create(null, memberTypes));
                            } else {
                                memberTupleTypes.addAll(memberTypes);
                            }
                        }
                        tupleTypeNode = new BTupleType(memberTupleTypes);
                        break;
                    }

                    if (possibleTypes.get(0).tag == TypeTags.TUPLE) {
                        tupleTypeNode = (BTupleType) possibleTypes.get(0);
                        break;
                    }

                    List<BType> memberTypes = new ArrayList<>();
                    for (int i = 0; i < varNode.memberVariables.size(); i++) {
                        memberTypes.add(possibleTypes.get(0));
                    }
                    tupleTypeNode = new BTupleType(memberTypes);
                    break;
                case TypeTags.ANY:
                case TypeTags.ANYDATA:
                    List<BType> memberTupleTypes = new ArrayList<>();
                    for (int i = 0; i < varNode.memberVariables.size(); i++) {
                        memberTupleTypes.add(varNode.type);
                    }
                    tupleTypeNode = new BTupleType(memberTupleTypes);
                    if (varNode.restVariable != null) {
                        tupleTypeNode.restType = varNode.type;
                    }
                    break;
                case TypeTags.TUPLE:
                    tupleTypeNode = (BTupleType) varNode.type;
                    break;
                case TypeTags.ARRAY:
                    List<BType> tupleTypes = new ArrayList<>();
                    BArrayType arrayType = (BArrayType) varNode.type;
                    for (int i = 0; i < arrayType.size; i++) {
                        tupleTypes.add(arrayType.eType);
                    }
                    tupleTypeNode = new BTupleType(tupleTypes);
                    break;
                default:
                    dlog.error(varNode.pos, DiagnosticErrorCode.INVALID_LIST_BINDING_PATTERN_DECL, varNode.type);
                    return false;
            }
        }

        if (!checkMemVarCountMatchWithMemTypeCount(varNode, tupleTypeNode)) {
            dlog.error(varNode.pos, DiagnosticErrorCode.INVALID_LIST_BINDING_PATTERN);
            return false;
        }

        int ignoredCount = 0;
        int i = 0;
        BType type;
        for (BLangVariable var : varNode.memberVariables) {
            type = tupleTypeNode.tupleTypes.get(i);
            i++;
            if (var.getKind() == NodeKind.VARIABLE) {
                // '_' is allowed in tuple variables. Not allowed if all variables are named as '_'
                BLangSimpleVariable simpleVar = (BLangSimpleVariable) var;
                Name varName = names.fromIdNode(simpleVar.name);
                if (varName == Names.IGNORE) {
                    ignoredCount++;
                    simpleVar.type = symTable.anyType;
                    types.checkType(varNode.pos, type, simpleVar.type,
                            DiagnosticErrorCode.INCOMPATIBLE_TYPES);
                    continue;
                }
            }
            defineMemberNode(var, env, type);
        }

        if (varNode.restVariable != null) {
            int tupleNodeMemCount = tupleTypeNode.tupleTypes.size();
            int varNodeMemCount = varNode.memberVariables.size();
            BType restType = tupleTypeNode.restType;
            if (varNodeMemCount < tupleNodeMemCount) {
                LinkedHashSet<BType> varTypes = new LinkedHashSet<>();
                for (int j = varNodeMemCount; j < tupleNodeMemCount; j++) {
                    varTypes.add(tupleTypeNode.tupleTypes.get(j));
                }
                if (restType != null) {
                    varTypes.add(restType);
                }
                if (varTypes.size() > 1) {
                    restType = BUnionType.create(null, varTypes);
                } else {
                    restType = varTypes.iterator().next();
                }
            }
            if (restType != null) {
                type = new BArrayType(restType);
            } else {
                LinkedHashSet<BType> varTypes = new LinkedHashSet<>(tupleTypeNode.tupleTypes);
                type = new BArrayType(BUnionType.create(null, varTypes));
            }
            defineMemberNode(varNode.restVariable, env, type);
        }

        if (!varNode.memberVariables.isEmpty() && ignoredCount == varNode.memberVariables.size()
                && varNode.restVariable == null) {
            dlog.error(varNode.pos, DiagnosticErrorCode.NO_NEW_VARIABLES_VAR_ASSIGNMENT);
            return false;
        }
        return true;
    }

    private boolean checkMemVarCountMatchWithMemTypeCount(BLangTupleVariable varNode, BTupleType tupleTypeNode) {
        int memberVarsSize = varNode.memberVariables.size();
        BLangVariable restVariable = varNode.restVariable;
        int tupleTypesSize = tupleTypeNode.tupleTypes.size();
        if (memberVarsSize > tupleTypesSize) {
            return false;
        }
        return restVariable != null ||
                (tupleTypesSize == memberVarsSize && tupleTypeNode.restType == null);
    }

    @Override
    public void visit(BLangRecordVariable recordVar) {
        if (recordVar.isDeclaredWithVar) {
            recordVar.symbol = defineVarSymbol(recordVar.pos, recordVar.flagSet, symTable.noType,
                    names.fromString(anonymousModelHelper.getNextRecordVarKey(env.enclPkg.packageID)), env,
                    recordVar.internal);
            // Symbol enter each member with type other.
            for (BLangRecordVariable.BLangRecordVariableKeyValue variable : recordVar.variableList) {
                BLangVariable value = variable.getValue();
                value.isDeclaredWithVar = true;
                defineNode(value, env);
            }

            BLangSimpleVariable restParam = (BLangSimpleVariable) recordVar.restParam;
            if (restParam != null) {
                restParam.isDeclaredWithVar = true;
                defineNode(restParam, env);
            }
            return;
        }

        if (recordVar.type == null) {
            recordVar.type = symResolver.resolveTypeNode(recordVar.typeNode, env);
        }
        // To support variable forward referencing we need to symbol enter each record member with type at SymbolEnter.
        if (!(symbolEnterAndValidateRecordVariable(recordVar, env))) {
            recordVar.type = symTable.semanticError;
            return;
        }
    }

    boolean symbolEnterAndValidateRecordVariable(BLangRecordVariable var, SymbolEnv env) {
        if (var.symbol == null) {
            Name varName = names.fromString(anonymousModelHelper.getNextRecordVarKey(env.enclPkg.packageID));
            var.symbol = defineVarSymbol(var.pos, var.flagSet, var.type, varName, env, var.internal);
        }

        return validateRecordVariable(var, env);
    }

    boolean validateRecordVariable(BLangRecordVariable recordVar, SymbolEnv env) {
        BRecordType recordVarType;
        /*
          This switch block will resolve the record type of the record variable.
          For example consider the following -
          type Foo record {int a, boolean b};
          type Bar record {string a, float b};
          Foo|Bar {a, b} = foo();
          Since the varNode type is a union, the types of 'a' and 'b' will be resolved as follows:
          Type of 'a' will be a union of the types of field 'a' in both Foo and Bar.
          i.e. type of 'a' is (int | string) and type of 'b' is (boolean | float).
          Consider anydata {a, b} = foo();
          Here, the type of 'a'and type of 'b' will be both anydata.
         */
        switch (recordVar.type.tag) {
            case TypeTags.UNION:
                BUnionType unionType = (BUnionType) recordVar.type;
                Set<BType> bTypes = types.expandAndGetMemberTypesRecursive(unionType);
                List<BType> possibleTypes = bTypes.stream()
                        .filter(rec -> doesRecordContainKeys(rec, recordVar.variableList, recordVar.restParam != null))
                        .collect(Collectors.toList());

                if (possibleTypes.isEmpty()) {
                    dlog.error(recordVar.pos, DiagnosticErrorCode.INVALID_RECORD_BINDING_PATTERN, recordVar.type);
                    return false;
                }

                if (possibleTypes.size() > 1) {
                    recordVarType = populatePossibleFields(recordVar, possibleTypes, env);
                    break;
                }

                if (possibleTypes.get(0).tag == TypeTags.RECORD) {
                    recordVarType = (BRecordType) possibleTypes.get(0);
                    break;
                }

                if (possibleTypes.get(0).tag == TypeTags.MAP) {
                    recordVarType = createSameTypedFieldsRecordType(recordVar,
                            ((BMapType) possibleTypes.get(0)).constraint, env);
                    break;
                }

                recordVarType = createSameTypedFieldsRecordType(recordVar, possibleTypes.get(0), env);
                break;
            case TypeTags.RECORD:
                recordVarType = (BRecordType) recordVar.type;
                break;
            case TypeTags.MAP:
                recordVarType = createSameTypedFieldsRecordType(recordVar, ((BMapType) recordVar.type).constraint, env);
                break;
            default:
                dlog.error(recordVar.pos, DiagnosticErrorCode.INVALID_RECORD_BINDING_PATTERN, recordVar.type);
                return false;
        }

        return defineVariableList(recordVar, recordVarType, env);
    }

    private BRecordType populatePossibleFields(BLangRecordVariable recordVar, List<BType> possibleTypes,
                                               SymbolEnv env) {
        BRecordTypeSymbol recordSymbol = Symbols.createRecordSymbol(Flags.ANONYMOUS,
                names.fromString(ANONYMOUS_RECORD_NAME),
                env.enclPkg.symbol.pkgID, null,
                env.scope.owner, recordVar.pos, SOURCE);
        BRecordType recordVarType = (BRecordType) symTable.recordType;

        LinkedHashMap<String, BField> fields =
                populateAndGetPossibleFieldsForRecVar(recordVar, possibleTypes, recordSymbol, env);

        if (recordVar.restParam != null) {
            LinkedHashSet<BType> memberTypes = possibleTypes.stream()
                    .map(possibleType -> {
                        if (possibleType.tag == TypeTags.RECORD) {
                            return ((BRecordType) possibleType).restFieldType;
                        } else if (possibleType.tag == TypeTags.MAP) {
                            return ((BMapType) possibleType).constraint;
                        } else {
                            return possibleType;
                        }
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            recordVarType.restFieldType = memberTypes.size() > 1 ?
                    BUnionType.create(null, memberTypes) :
                    memberTypes.iterator().next();
        }

        recordVarType.tsymbol = recordSymbol;
        recordVarType.fields = fields;
        recordSymbol.type = recordVarType;
        return recordVarType;
    }

    /**
     * This method will resolve field types based on a list of possible types.
     * When a record variable has multiple possible assignable types, each field will be a union of the relevant
     * possible types field type.
     *
     * @param recordVar record variable whose fields types are to be resolved
     * @param possibleTypes list of possible types
     * @param recordSymbol symbol of the record type to be used in creating fields
     * @return the list of fields
     */
    private LinkedHashMap<String, BField> populateAndGetPossibleFieldsForRecVar(BLangRecordVariable recordVar,
                                                                                List<BType> possibleTypes,
                                                                                BRecordTypeSymbol recordSymbol,
                                                                                SymbolEnv env) {
        LinkedHashMap<String, BField> fields = new LinkedHashMap<>();
        for (BLangRecordVariable.BLangRecordVariableKeyValue bLangRecordVariableKeyValue : recordVar.variableList) {
            String fieldName = bLangRecordVariableKeyValue.key.value;
            LinkedHashSet<BType> memberTypes = new LinkedHashSet<>();
            for (BType possibleType : possibleTypes) {
                if (possibleType.tag == TypeTags.RECORD) {
                    BRecordType possibleRecordType = (BRecordType) possibleType;

                    if (possibleRecordType.fields.containsKey(fieldName)) {
                        BField field = possibleRecordType.fields.get(fieldName);
                        if (Symbols.isOptional(field.symbol)) {
                            memberTypes.add(symTable.nilType);
                        }
                        memberTypes.add(field.type);
                    } else {
                        memberTypes.add(possibleRecordType.restFieldType);
                        memberTypes.add(symTable.nilType);
                    }

                    continue;
                }

                if (possibleType.tag == TypeTags.MAP) {
                    BMapType possibleMapType = (BMapType) possibleType;
                    memberTypes.add(possibleMapType.constraint);
                    continue;
                }
                memberTypes.add(possibleType); // possible type is any or anydata}
            }

            BType fieldType = memberTypes.size() > 1 ?
                    BUnionType.create(null, memberTypes) : memberTypes.iterator().next();
            BField field = new BField(names.fromString(fieldName), recordVar.pos,
                    new BVarSymbol(0, names.fromString(fieldName), env.enclPkg.symbol.pkgID,
                            fieldType, recordSymbol, recordVar.pos, SOURCE));
            fields.put(field.name.value, field);
        }
        return fields;
    }

    private BRecordType createSameTypedFieldsRecordType(BLangRecordVariable recordVar, BType fieldTypes,
                                                        SymbolEnv env) {
        BType fieldType;
        if (fieldTypes.isNullable()) {
            fieldType = fieldTypes;
        } else {
            fieldType = BUnionType.create(null, fieldTypes, symTable.nilType);
        }

        BRecordTypeSymbol recordSymbol = Symbols.createRecordSymbol(Flags.ANONYMOUS,
                names.fromString(ANONYMOUS_RECORD_NAME),
                env.enclPkg.symbol.pkgID, null, env.scope.owner,
                recordVar.pos, SOURCE);
        //TODO check below field position
        LinkedHashMap<String, BField> fields = new LinkedHashMap<>();
        for (BLangRecordVariable.BLangRecordVariableKeyValue bLangRecordVariableKeyValue : recordVar.variableList) {
            String fieldName = bLangRecordVariableKeyValue.key.value;
            BField bField = new BField(names.fromString(fieldName), recordVar.pos,
                    new BVarSymbol(0, names.fromString(fieldName), env.enclPkg.symbol.pkgID,
                            fieldType, recordSymbol, recordVar.pos, SOURCE));
            fields.put(fieldName, bField);
        }

        BRecordType recordVarType = (BRecordType) symTable.recordType;
        recordVarType.fields = fields;
        recordSymbol.type = recordVarType;
        recordVarType.tsymbol = recordSymbol;

        // Since this is for record variables, we consider its record type as an open record type.
        recordVarType.sealed = false;
        recordVarType.restFieldType = fieldTypes; // TODO: 7/26/19 Check if this should be `fieldType`

        return recordVarType;
    }

    private boolean defineVariableList(BLangRecordVariable recordVar, BRecordType recordVarType, SymbolEnv env) {
        LinkedHashMap<String, BField> recordVarTypeFields = recordVarType.fields;

        boolean validRecord = true;
        int ignoredCount = 0;
        for (BLangRecordVariable.BLangRecordVariableKeyValue variable : recordVar.variableList) {
            // Infer the type of each variable in recordVariable from the given record type
            // so that symbol enter is done recursively
            if (names.fromIdNode(variable.getKey()) == Names.IGNORE) {
                dlog.error(recordVar.pos, DiagnosticErrorCode.UNDERSCORE_NOT_ALLOWED);
                continue;
            }

            BLangVariable value = variable.getValue();
            if (value.getKind() == NodeKind.VARIABLE) {
                // '_' is allowed in record variables. Not allowed if all variables are named as '_'
                BLangSimpleVariable simpleVar = (BLangSimpleVariable) value;
                Name varName = names.fromIdNode(simpleVar.name);
                if (varName == Names.IGNORE) {
                    ignoredCount++;
                    simpleVar.type = symTable.anyType;
                    if (!recordVarTypeFields.containsKey(variable.getKey().getValue())) {
                        continue;
                    }
                    types.checkType(variable.valueBindingPattern.pos,
                            recordVarTypeFields.get((variable.getKey().getValue())).type, simpleVar.type,
                            DiagnosticErrorCode.INCOMPATIBLE_TYPES);
                    continue;
                }
            }

            if (!recordVarTypeFields.containsKey(variable.getKey().getValue())) {
                if (recordVarType.sealed) {
                    validRecord = false;
                    dlog.error(recordVar.pos, DiagnosticErrorCode.INVALID_FIELD_IN_RECORD_BINDING_PATTERN,
                            variable.getKey().getValue(), recordVar.type);
                } else {
                    BType restType;
                    if (recordVarType.restFieldType.tag == TypeTags.ANYDATA ||
                            recordVarType.restFieldType.tag == TypeTags.ANY) {
                        restType = recordVarType.restFieldType;
                    } else {
                        restType = BUnionType.create(null, recordVarType.restFieldType, symTable.nilType);
                    }
                    defineMemberNode(value, env, restType);
                }
                continue;
            }
            defineMemberNode(value, env, recordVarTypeFields.get((variable.getKey().getValue())).type);
        }

        if (!recordVar.variableList.isEmpty() && ignoredCount == recordVar.variableList.size()
                && recordVar.restParam == null) {
            dlog.error(recordVar.pos, DiagnosticErrorCode.NO_NEW_VARIABLES_VAR_ASSIGNMENT);
            return false;
        }

        if (recordVar.restParam != null) {
            defineMemberNode(((BLangSimpleVariable) recordVar.restParam), env, getRestParamType(recordVarType));
        }

        return validRecord;
    }

    private boolean doesRecordContainKeys(BType varType,
                                          List<BLangRecordVariable.BLangRecordVariableKeyValue> variableList,
                                          boolean hasRestParam) {
        if (varType.tag == TypeTags.MAP || varType.tag == TypeTags.ANY || varType.tag == TypeTags.ANYDATA) {
            return true;
        }
        if (varType.tag != TypeTags.RECORD) {
            return false;
        }
        BRecordType recordVarType = (BRecordType) varType;
        Map<String, BField> recordVarTypeFields = recordVarType.fields;

        for (BLangRecordVariable.BLangRecordVariableKeyValue var : variableList) {
            if (!recordVarTypeFields.containsKey(var.key.value) && recordVarType.sealed) {
                return false;
            }
        }

        if (!hasRestParam) {
            return true;
        }

        return !recordVarType.sealed;
    }

    BMapType getRestParamType(BRecordType recordType)  {
        BType memberType;

        if (hasErrorTypedField(recordType)) {
            memberType = hasOnlyPureTypedFields(recordType) ? symTable.pureType :
                    BUnionType.create(null, symTable.anyType, symTable.errorType);
        } else {
            memberType = hasOnlyAnyDataTypedFields(recordType) ? symTable.anydataType : symTable.anyType;
        }

        return new BMapType(TypeTags.MAP, memberType, null);
    }

    private boolean hasOnlyAnyDataTypedFields(BRecordType recordType) {
        IsAnydataUniqueVisitor isAnydataUniqueVisitor = new IsAnydataUniqueVisitor();
        return isAnydataUniqueVisitor.visit(recordType);
    }

    private boolean hasOnlyPureTypedFields(BRecordType recordType) {
        IsPureTypeUniqueVisitor isPureTypeUniqueVisitor = new IsPureTypeUniqueVisitor();
        for (BField field : recordType.fields.values()) {
            BType fieldType = field.type;
            if (!isPureTypeUniqueVisitor.visit(fieldType)) {
                return false;
            }
            isPureTypeUniqueVisitor.reset();
        }
        return recordType.sealed || isPureTypeUniqueVisitor.visit(recordType);
    }

    private boolean hasErrorTypedField(BRecordType recordType) {
        for (BField field : recordType.fields.values()) {
            BType type = field.type;
            if (hasErrorType(type)) {
                return true;
            }
        }
        return hasErrorType(recordType.restFieldType);
    }

    private boolean hasErrorType(BType type) {
        if (type.tag != TypeTags.UNION) {
            return type.tag == TypeTags.ERROR;
        }

        return ((BUnionType) type).getMemberTypes().stream().anyMatch(this::hasErrorType);
    }

    @Override
    public void visit(BLangErrorVariable errorVar) {
        if (errorVar.isDeclaredWithVar) {
            errorVar.symbol = defineVarSymbol(errorVar.pos, errorVar.flagSet, symTable.noType,
                    names.fromString(anonymousModelHelper.getNextErrorVarKey(env.enclPkg.packageID)), env,
                    errorVar.internal);

            // Symbol enter each member with type other.
            BLangSimpleVariable errorMsg = errorVar.message;
            if (errorMsg != null) {
                errorMsg.isDeclaredWithVar = true;
                defineNode(errorMsg, env);
            }

            BLangVariable cause = errorVar.cause;
            if (cause != null) {
                cause.isDeclaredWithVar = true;
                defineNode(cause, env);
            }

            for (BLangErrorVariable.BLangErrorDetailEntry detailEntry: errorVar.detail) {
                BLangVariable value = detailEntry.getValue();
                value.isDeclaredWithVar = true;
                defineNode(value, env);
            }

            BLangSimpleVariable restDetail = errorVar.restDetail;
            if (restDetail != null) {
                restDetail.isDeclaredWithVar = true;
                defineNode(restDetail, env);
            }

            return;
        }

        if (errorVar.type == null) {
            errorVar.type = symResolver.resolveTypeNode(errorVar.typeNode, env);
        }
        // To support variable forward referencing we need to symbol enter each variable inside error variable
        // with type at SymbolEnter.
        if (!symbolEnterAndValidateErrorVariable(errorVar, env)) {
            errorVar.type = symTable.semanticError;
            return;
        }
    }

    boolean symbolEnterAndValidateErrorVariable(BLangErrorVariable var, SymbolEnv env) {
        if (var.symbol == null) {
            Name varName = names.fromString(anonymousModelHelper.getNextErrorVarKey(env.enclPkg.packageID));
            var.symbol = defineVarSymbol(var.pos, var.flagSet, var.type, varName, env, var.internal);
        }

        return validateErrorVariable(var, env);
    }

    boolean validateErrorVariable(BLangErrorVariable errorVariable, SymbolEnv env) {
        BErrorType errorType;
        switch (errorVariable.type.tag) {
            case TypeTags.UNION:
                BUnionType unionType = ((BUnionType) errorVariable.type);
                List<BErrorType> possibleTypes = unionType.getMemberTypes().stream()
                        .filter(type -> TypeTags.ERROR == type.tag)
                        .map(BErrorType.class::cast)
                        .collect(Collectors.toList());

                if (possibleTypes.isEmpty()) {
                    dlog.error(errorVariable.pos, DiagnosticErrorCode.INVALID_ERROR_BINDING_PATTERN,
                            errorVariable.type);
                    return false;
                }

                if (possibleTypes.size() > 1) {
                    LinkedHashSet<BType> detailType = new LinkedHashSet<>();
                    for (BErrorType possibleErrType : possibleTypes) {
                        detailType.add(possibleErrType.detailType);
                    }
                    BType errorDetailType = detailType.size() > 1
                            ? BUnionType.create(null, detailType)
                            : detailType.iterator().next();
                    errorType = new BErrorType(null, errorDetailType);
                } else {
                    errorType = possibleTypes.get(0);
                }
                break;
            case TypeTags.ERROR:
                errorType = (BErrorType) errorVariable.type;
                break;
            default:
                dlog.error(errorVariable.pos, DiagnosticErrorCode.INVALID_ERROR_BINDING_PATTERN, errorVariable.type);
                return false;
        }
        errorVariable.type = errorType;

        if (!errorVariable.isInMatchStmt) {
            BLangSimpleVariable errorMsg = errorVariable.message;
            if (errorMsg != null) {
                defineMemberNode(errorVariable.message, env, symTable.stringType);
            }

            BLangVariable cause = errorVariable.cause;
            if (cause != null) {
                defineMemberNode(errorVariable.cause, env, symTable.errorOrNilType);
            }
        }

        if (errorVariable.detail == null || (errorVariable.detail.isEmpty()
                && !isRestDetailBindingAvailable(errorVariable))) {
            return validateErrorMessageMatchPatternSyntax(errorVariable, env);
        }

        if (errorType.detailType.getKind() == TypeKind.RECORD || errorType.detailType.getKind() == TypeKind.MAP) {
            return validateErrorVariable(errorVariable, errorType, env);
        } else if (errorType.detailType.getKind() == TypeKind.UNION) {
            BErrorTypeSymbol errorTypeSymbol = new BErrorTypeSymbol(SymTag.ERROR, Flags.PUBLIC, Names.ERROR,
                    env.enclPkg.packageID, symTable.errorType,
                    env.scope.owner, errorVariable.pos, SOURCE);
            // TODO: detail type need to be a union representing all details of members of `errorType`
            errorVariable.type = new BErrorType(errorTypeSymbol, symTable.detailType);
            return validateErrorVariable(errorVariable, env);
        }

        if (isRestDetailBindingAvailable(errorVariable)) {
            defineMemberNode(errorVariable.restDetail, env, symTable.detailType);
        }
        return true;
    }

    private boolean validateErrorVariable(BLangErrorVariable errorVariable, BErrorType errorType, SymbolEnv env) {
        BLangSimpleVariable errorMsg = errorVariable.message;
        if (errorMsg != null && errorMsg.symbol == null) {
            defineMemberNode(errorMsg, env, symTable.stringType);
        }

        BRecordType recordType = getDetailAsARecordType(errorType);
        LinkedHashMap<String, BField> detailFields = recordType.fields;
        Set<String> matchedDetailFields = new HashSet<>();
        for (BLangErrorVariable.BLangErrorDetailEntry errorDetailEntry : errorVariable.detail) {
            String entryName = errorDetailEntry.key.getValue();
            matchedDetailFields.add(entryName);
            BField entryField = detailFields.get(entryName);

            BLangVariable boundVar = errorDetailEntry.valueBindingPattern;
            if (entryField != null) {
                if ((entryField.symbol.flags & Flags.OPTIONAL) == Flags.OPTIONAL) {
                    boundVar.type = BUnionType.create(null, entryField.type, symTable.nilType);
                } else {
                    boundVar.type = entryField.type;
                }
            } else {
                if (recordType.sealed) {
                    dlog.error(errorVariable.pos, DiagnosticErrorCode.INVALID_ERROR_BINDING_PATTERN,
                            errorVariable.type);
                    boundVar.type = symTable.semanticError;
                    return false;
                } else {
                    boundVar.type = BUnionType.create(null, recordType.restFieldType, symTable.nilType);
                }
            }

            boolean isIgnoredVar = boundVar.getKind() == NodeKind.VARIABLE
                    && ((BLangSimpleVariable) boundVar).name.value.equals(Names.IGNORE.value);
            if (!isIgnoredVar) {
                defineMemberNode(boundVar, env, boundVar.type);
            }
        }

        if (isRestDetailBindingAvailable(errorVariable)) {
            // Type of rest pattern is a map type where constraint type is,
            // union of keys whose values are not matched in error binding/match pattern.
            BTypeSymbol typeSymbol = createTypeSymbol(SymTag.TYPE, env);
            BType constraint = getRestMapConstraintType(detailFields, matchedDetailFields, recordType);
            BMapType restType = new BMapType(TypeTags.MAP, constraint, typeSymbol);
            typeSymbol.type = restType;
            errorVariable.restDetail.type = restType;
            defineMemberNode(errorVariable.restDetail, env, restType);
        }
        return true;
    }

    BRecordType getDetailAsARecordType(BErrorType errorType) {
        if (errorType.detailType.getKind() == TypeKind.RECORD) {
            return (BRecordType) errorType.detailType;
        }
        BRecordType detailRecord = new BRecordType(null);
        BMapType detailMap = (BMapType) errorType.detailType;
        detailRecord.sealed = false;
        detailRecord.restFieldType = detailMap.constraint;
        return detailRecord;
    }

    private BType getRestMapConstraintType(Map<String, BField> errorDetailFields, Set<String> matchedDetailFields,
                                           BRecordType recordType) {
        BUnionType restUnionType = BUnionType.create(null);
        if (!recordType.sealed) {
            if (recordType.restFieldType.tag == TypeTags.UNION) {
                BUnionType restFieldUnion = (BUnionType) recordType.restFieldType;
                // This is to update type name for users to read easily the cyclic unions
                if (restFieldUnion.isCyclic && errorDetailFields.entrySet().isEmpty()) {
                    restUnionType.isCyclic = true;
                    restUnionType.tsymbol = restFieldUnion.tsymbol;
                }
            } else {
                restUnionType.add(recordType.restFieldType);
            }
        }
        for (Map.Entry<String, BField> entry : errorDetailFields.entrySet()) {
            if (!matchedDetailFields.contains(entry.getKey())) {
                BType type = entry.getValue().getType();
                if (!types.isAssignable(type, restUnionType)) {
                    restUnionType.add(type);
                }
            }
        }

        Set<BType> memberTypes = restUnionType.getMemberTypes();
        if (memberTypes.size() == 1) {
            return memberTypes.iterator().next();
        }

        return restUnionType;
    }

    private boolean validateErrorMessageMatchPatternSyntax(BLangErrorVariable errorVariable, SymbolEnv env) {
        if (errorVariable.isInMatchStmt
                && !errorVariable.reasonVarPrefixAvailable
                && errorVariable.reasonMatchConst == null
                && isReasonSpecified(errorVariable)) {

            BSymbol reasonConst = symResolver.lookupSymbolInMainSpace(env.enclEnv,
                    names.fromString(errorVariable.message.name.value));
            if ((reasonConst.tag & SymTag.CONSTANT) != SymTag.CONSTANT) {
                dlog.error(errorVariable.message.pos, DiagnosticErrorCode.INVALID_ERROR_REASON_BINDING_PATTERN,
                        errorVariable.message.name);
            } else {
                dlog.error(errorVariable.message.pos, DiagnosticErrorCode.UNSUPPORTED_ERROR_REASON_CONST_MATCH);
            }
            return false;
        }
        return true;
    }

    private boolean isReasonSpecified(BLangErrorVariable errorVariable) {
        return !isIgnoredOrEmpty(errorVariable.message);
    }

    boolean isIgnoredOrEmpty(BLangSimpleVariable varNode) {
        return varNode.name.value.equals(Names.IGNORE.value) || varNode.name.value.equals("");
    }

    private boolean isRestDetailBindingAvailable(BLangErrorVariable errorVariable) {
        return errorVariable.restDetail != null &&
                !errorVariable.restDetail.name.value.equals(Names.IGNORE.value);
    }

    private BTypeSymbol createTypeSymbol(int type, SymbolEnv env) {
        return new BTypeSymbol(type, Flags.PUBLIC, Names.EMPTY, env.enclPkg.packageID,
                null, env.scope.owner, symTable.builtinPos, VIRTUAL);
    }

    private void defineMemberNode(BLangVariable memberVar, SymbolEnv env, BType type) {
        memberVar.type = type;
        // Module level variables declared with `var` already defined
        if ((env.scope.owner.tag & SymTag.PACKAGE) == SymTag.PACKAGE && memberVar.isDeclaredWithVar) {
            memberVar.symbol.type = type;
            memberVar.isDeclaredWithVar = false;
            // Need to assign resolved type for member variables inside complex variable declared with `var`
            if (memberVar.getKind() == NodeKind.VARIABLE) {
                return;
            }
        }
        defineNode(memberVar, env);
    }

    public void visit(BLangXMLAttribute bLangXMLAttribute) {
        if (!(bLangXMLAttribute.name.getKind() == NodeKind.XML_QNAME)) {
            return;
        }

        BLangXMLQName qname = (BLangXMLQName) bLangXMLAttribute.name;

        // If the attribute is not an in-line namespace declaration, check for duplicate attributes.
        // If no duplicates, then define this attribute symbol.
        if (!bLangXMLAttribute.isNamespaceDeclr) {
            BXMLAttributeSymbol attrSymbol = new BXMLAttributeSymbol(qname.localname.value, qname.namespaceURI,
                                                                     env.enclPkg.symbol.pkgID, env.scope.owner,
                                                                     bLangXMLAttribute.pos, SOURCE);

            if (missingNodesHelper.isMissingNode(qname.localname.value)
                    || (qname.namespaceURI != null && missingNodesHelper.isMissingNode(qname.namespaceURI))) {
                attrSymbol.origin = VIRTUAL;
            }
            if (symResolver.checkForUniqueMemberSymbol(bLangXMLAttribute.pos, env, attrSymbol)) {
                env.scope.define(attrSymbol.name, attrSymbol);
                bLangXMLAttribute.symbol = attrSymbol;
            }
            return;
        }

        List<BLangExpression> exprs = bLangXMLAttribute.value.textFragments;
        String nsURI = null;

        // We reach here if the attribute is an in-line namesapce declaration.
        // Get the namespace URI, only if it is statically defined. Then define the namespace symbol.
        // This namespace URI is later used by the attributes, when they lookup for duplicate attributes.
        // TODO: find a better way to get the statically defined URI.
        NodeKind nodeKind = exprs.get(0).getKind();
        if (exprs.size() == 1 && (nodeKind == NodeKind.LITERAL || nodeKind == NodeKind.NUMERIC_LITERAL)) {
            nsURI = (String) ((BLangLiteral) exprs.get(0)).value;
        }

        String symbolName = qname.localname.value;
        if (symbolName.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            symbolName = XMLConstants.DEFAULT_NS_PREFIX;
        }

        Name prefix = names.fromString(symbolName);
        BXMLNSSymbol xmlnsSymbol = new BXMLNSSymbol(prefix, nsURI, env.enclPkg.symbol.pkgID, env.scope.owner,
                                                    qname.localname.pos, getOrigin(prefix));

        if (symResolver.checkForUniqueMemberSymbol(bLangXMLAttribute.pos, env, xmlnsSymbol)) {
            env.scope.define(xmlnsSymbol.name, xmlnsSymbol);
            bLangXMLAttribute.symbol = xmlnsSymbol;
        }
    }

    @Override
    public void visit(BLangRecordTypeNode recordTypeNode) {
        SymbolEnv typeDefEnv = SymbolEnv.createTypeEnv(recordTypeNode, recordTypeNode.symbol.scope, env);
        defineRecordTypeNode(recordTypeNode, typeDefEnv);
    }

    private void defineRecordTypeNode(BLangRecordTypeNode recordTypeNode, SymbolEnv env) {
        BRecordType recordType = (BRecordType) recordTypeNode.symbol.type;
        recordTypeNode.type = recordType;

        // Define all the fields
        resolveFields(recordType, recordTypeNode, env);

        recordType.sealed = recordTypeNode.sealed;
        if (recordTypeNode.sealed && recordTypeNode.restFieldType != null) {
            dlog.error(recordTypeNode.restFieldType.pos, DiagnosticErrorCode.REST_FIELD_NOT_ALLOWED_IN_CLOSED_RECORDS);
            return;
        }

        List<BType> fieldTypes = new ArrayList<>(recordType.fields.size());
        for (BField field : recordType.fields.values()) {
            BType type = field.type;
            fieldTypes.add(type);
        }

        if (recordTypeNode.restFieldType == null) {
            symResolver.markParameterizedType(recordType, fieldTypes);
            if (recordTypeNode.sealed) {
                recordType.restFieldType = symTable.noType;
                return;
            }
            recordType.restFieldType = symTable.anydataType;
            return;
        }

        recordType.restFieldType = symResolver.resolveTypeNode(recordTypeNode.restFieldType, env);
        fieldTypes.add(recordType.restFieldType);
        symResolver.markParameterizedType(recordType, fieldTypes);
    }

    private Collector<BField, ?, LinkedHashMap<String, BField>> getFieldCollector() {
        BinaryOperator<BField> mergeFunc = (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
        return Collectors.toMap(field -> field.name.value, Function.identity(), mergeFunc, LinkedHashMap::new);
    }

    // Private methods

    private void populateLangLibInSymTable(BPackageSymbol packageSymbol) {

        PackageID langLib = packageSymbol.pkgID;
        if (langLib.equals(ARRAY)) {
            symTable.langArrayModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(DECIMAL)) {
            symTable.langDecimalModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(ERROR)) {
            symTable.langErrorModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(FLOAT)) {
            symTable.langFloatModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(FUTURE)) {
            symTable.langFutureModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(INT)) {
            symTable.langIntModuleSymbol = packageSymbol;
            symTable.updateIntSubtypeOwners();
            return;
        }
        if (langLib.equals(MAP)) {
            symTable.langMapModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(OBJECT)) {
            symTable.langObjectModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(STREAM)) {
            symTable.langStreamModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(STRING)) {
            symTable.langStringModuleSymbol = packageSymbol;
            symTable.updateStringSubtypeOwners();
            return;
        }
        if (langLib.equals(TABLE)) {
            symTable.langTableModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(TYPEDESC)) {
            symTable.langTypedescModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(VALUE)) {
            symTable.langValueModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(XML)) {
            symTable.langXmlModuleSymbol = packageSymbol;
            symTable.updateXMLSubtypeOwners();
            return;
        }
        if (langLib.equals(BOOLEAN)) {
            symTable.langBooleanModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(QUERY)) {
            symTable.langQueryModuleSymbol = packageSymbol;
            return;
        }
        if (langLib.equals(TRANSACTION)) {
            symTable.langTransactionModuleSymbol = packageSymbol;
            return;
        }
    }

    public boolean isValidAnnotationType(BType type) {
        if (type == symTable.semanticError) {
            return false;
        }

        switch (type.tag) {
            case TypeTags.MAP:
                BType constraintType = ((BMapType) type).constraint;
                return isCloneableTypeTypeSkippingObjectType(constraintType);
            case TypeTags.RECORD:
                BRecordType recordType = (BRecordType) type;
                for (BField field : recordType.fields.values()) {
                    if (!isCloneableTypeTypeSkippingObjectType(field.type)) {
                        return false;
                    }
                }

                BType recordRestType = recordType.restFieldType;
                if (recordRestType == null || recordRestType == symTable.noType) {
                    return true;
                }

                return isCloneableTypeTypeSkippingObjectType(recordRestType);
            case TypeTags.ARRAY:
                BType elementType = ((BArrayType) type).eType;
                if ((elementType.tag == TypeTags.MAP) || (elementType.tag == TypeTags.RECORD)) {
                    return isValidAnnotationType(elementType);
                }
                return false;
        }

        return types.isAssignable(type, symTable.trueType);
    }

    private boolean isCloneableTypeTypeSkippingObjectType(BType type) {
        return isCloneableTypeSkippingObjectTypeHelper(type, new HashSet<>());
    }

    private boolean isCloneableTypeSkippingObjectTypeHelper(BType type, Set<BType> unresolvedTypes) {
        if (type == symTable.semanticError) {
            return false;
        }

        if (!unresolvedTypes.add(type)) {
            return true;
        }

        switch (type.tag) {
            case TypeTags.OBJECT:
            case TypeTags.ANYDATA:
                return true;
            case TypeTags.RECORD:
                BRecordType recordType = (BRecordType) type;
                for (BField field : recordType.fields.values()) {
                    if (!isCloneableTypeSkippingObjectTypeHelper(field.type, unresolvedTypes)) {
                        return false;
                    }
                }
                BType recordRestType = recordType.restFieldType;
                if (recordRestType == null || recordRestType == symTable.noType) {
                    return true;
                }
                return isCloneableTypeSkippingObjectTypeHelper(recordRestType, unresolvedTypes);
            case TypeTags.MAP:
                BType constraintType = ((BMapType) type).constraint;
                return isCloneableTypeSkippingObjectTypeHelper(constraintType, unresolvedTypes);
            case TypeTags.UNION:
                for (BType memberType : ((BUnionType) type).getMemberTypes()) {
                    if (!isCloneableTypeSkippingObjectTypeHelper(memberType, unresolvedTypes)) {
                        return false;
                    }
                }
                return true;
            case TypeTags.TUPLE:
                BTupleType tupleType = (BTupleType) type;
                for (BType tupMemType : tupleType.getTupleTypes()) {
                    if (!isCloneableTypeSkippingObjectTypeHelper(tupMemType, unresolvedTypes)) {
                        return false;
                    }
                }
                BType tupRestType = tupleType.restType;
                if (tupRestType == null) {
                    return true;
                }
                return isCloneableTypeSkippingObjectTypeHelper(tupRestType, unresolvedTypes);
            case TypeTags.TABLE:
                return isCloneableTypeSkippingObjectTypeHelper(((BTableType) type).constraint, unresolvedTypes);
            case TypeTags.ARRAY:
                return isCloneableTypeSkippingObjectTypeHelper(((BArrayType) type).getElementType(),
                        unresolvedTypes);
        }

        return types.isAssignable(type, symTable.cloneableType);
    }


    /**
     * Visit each compilation unit (.bal file) and add each top-level node
     * in the compilation unit to the package node.
     *
     * @param pkgNode current package node
     */
    private void populatePackageNode(BLangPackage pkgNode) {
        List<BLangCompilationUnit> compUnits = pkgNode.getCompilationUnits();
        compUnits.forEach(compUnit -> populateCompilationUnit(pkgNode, compUnit));
    }

    /**
     * Visit each top-level node and add it to the package node.
     *
     * @param pkgNode  current package node
     * @param compUnit current compilation unit
     */
    private void populateCompilationUnit(BLangPackage pkgNode, BLangCompilationUnit compUnit) {
        compUnit.getTopLevelNodes().forEach(node -> addTopLevelNode(pkgNode, node));
    }

    private void addTopLevelNode(BLangPackage pkgNode, TopLevelNode node) {
        NodeKind kind = node.getKind();

        // Here we keep all the top-level nodes of a compilation unit (aka file) in exact same
        // order as they appear in the compilation unit. This list contains all the top-level
        // nodes of all the compilation units grouped by the compilation unit.
        // This allows other compiler phases to visit top-level nodes in the exact same order
        // as they appear in compilation units. This is required for error reporting.
        if (kind != NodeKind.PACKAGE_DECLARATION && kind != IMPORT) {
            pkgNode.topLevelNodes.add(node);
        }

        switch (kind) {
            case IMPORT:
                // TODO Verify the rules..
                // TODO Check whether the same package alias (if any) has been used for the same import
                // TODO The version of an import package can be specified only once for a package
                pkgNode.imports.add((BLangImportPackage) node);
                break;
            case FUNCTION:
                pkgNode.functions.add((BLangFunction) node);
                break;
            case TYPE_DEFINITION:
                pkgNode.typeDefinitions.add((BLangTypeDefinition) node);
                break;
            case SERVICE:
                pkgNode.services.add((BLangService) node);
                break;
            case VARIABLE:
            case TUPLE_VARIABLE:
            case RECORD_VARIABLE:
            case ERROR_VARIABLE:
                pkgNode.globalVars.add((BLangVariable) node);
                // TODO There are two kinds of package level variables, constant and regular variables.
                break;
            case ANNOTATION:
                // TODO
                pkgNode.annotations.add((BLangAnnotation) node);
                break;
            case XMLNS:
                pkgNode.xmlnsList.add((BLangXMLNS) node);
                break;
            case CONSTANT:
                pkgNode.constants.add((BLangConstant) node);
                break;
            case CLASS_DEFN:
                pkgNode.classDefinitions.add((BLangClassDefinition) node);
        }
    }

    private void defineErrorDetails(List<BLangTypeDefinition> typeDefNodes, SymbolEnv pkgEnv) {
        for (BLangTypeDefinition typeDef : typeDefNodes) {
            BLangType typeNode = typeDef.typeNode;
            if (typeNode.getKind() == NodeKind.ERROR_TYPE) {
                SymbolEnv typeDefEnv = SymbolEnv.createTypeEnv(typeNode, typeDef.symbol.scope, pkgEnv);
                BLangErrorType errorTypeNode = (BLangErrorType) typeNode;

                BType detailType = Optional.ofNullable(errorTypeNode.detailType)
                        .map(bLangType -> symResolver.resolveTypeNode(bLangType, typeDefEnv))
                        .orElse(symTable.detailType);

                ((BErrorType) typeDef.symbol.type).detailType = detailType;
            } else if (typeNode.type != null && typeNode.type.tag == TypeTags.ERROR) {
                SymbolEnv typeDefEnv = SymbolEnv.createTypeEnv(typeNode, typeDef.symbol.scope, pkgEnv);
                BType detailType = ((BErrorType) typeNode.type).detailType;
                if (detailType == symTable.noType) {
                    BErrorType type = (BErrorType) symResolver.resolveTypeNode(typeNode, typeDefEnv);
                    ((BErrorType) typeDef.symbol.type).detailType = type.detailType;
                }
            }
        }
    }

    private void defineFields(List<BLangNode> typeDefNodes, SymbolEnv pkgEnv) {
        for (BLangNode typeDef : typeDefNodes) {
            if (typeDef.getKind() == NodeKind.CLASS_DEFN) {
                defineFieldsOfClassDef((BLangClassDefinition) typeDef, pkgEnv);
            } else if (typeDef.getKind() == NodeKind.TYPE_DEFINITION) {
                defineFieldsOfObjectOrRecordTypeDef((BLangTypeDefinition) typeDef, pkgEnv);
            }
        }
    }

    private void defineFieldsOfClassDef(BLangClassDefinition classDefinition, SymbolEnv env) {
        SymbolEnv typeDefEnv = SymbolEnv.createClassEnv(classDefinition, classDefinition.symbol.scope, env);
        BObjectTypeSymbol tSymbol = (BObjectTypeSymbol) classDefinition.symbol;
        BObjectType objType = (BObjectType) tSymbol.type;

        for (BLangSimpleVariable field : classDefinition.fields) {
            defineNode(field, typeDefEnv);
            // Unless skipped, this causes issues in negative cases such as duplicate fields.
            if (field.symbol.type == symTable.semanticError) {
                continue;
            }
            objType.fields.put(field.name.value, new BField(names.fromIdNode(field.name), field.pos, field.symbol));
        }

        // todo: check for class fields and object fields
        defineReferencedClassFields(classDefinition, typeDefEnv, objType, false);
    }

    private void defineFieldsOfObjectOrRecordTypeDef(BLangTypeDefinition typeDef, SymbolEnv pkgEnv) {
        NodeKind nodeKind = typeDef.typeNode.getKind();
        if (nodeKind != NodeKind.OBJECT_TYPE && nodeKind != NodeKind.RECORD_TYPE) {
            return;
        }

        // Create typeDef type
        BStructureType structureType = (BStructureType) typeDef.symbol.type;
        BLangStructureTypeNode structureTypeNode = (BLangStructureTypeNode) typeDef.typeNode;
        SymbolEnv typeDefEnv = SymbolEnv.createTypeEnv(structureTypeNode, typeDef.symbol.scope, pkgEnv);

        // Define all the fields
        resolveFields(structureType, structureTypeNode, typeDefEnv);

        if (typeDef.symbol.kind != SymbolKind.RECORD) {
            return;
        }

        BLangRecordTypeNode recordTypeNode = (BLangRecordTypeNode) structureTypeNode;
        BRecordType recordType = (BRecordType) structureType;
        recordType.sealed = recordTypeNode.sealed;
        if (recordTypeNode.sealed && recordTypeNode.restFieldType != null) {
            dlog.error(recordTypeNode.restFieldType.pos, DiagnosticErrorCode.REST_FIELD_NOT_ALLOWED_IN_CLOSED_RECORDS);
            return;
        }

        if (recordTypeNode.restFieldType != null) {
            recordType.restFieldType = symResolver.resolveTypeNode(recordTypeNode.restFieldType, typeDefEnv);
            return;
        }

        if (!recordTypeNode.sealed) {
            recordType.restFieldType = symTable.anydataType;
            return;
        }

        // analyze restFieldType for open records
        for (BLangType typeRef : recordTypeNode.typeRefs) {
            if (typeRef.type.tag != TypeTags.RECORD) {
                continue;
            }
            BType restFieldType = ((BRecordType) typeRef.type).restFieldType;
            if (restFieldType == symTable.noType) {
                continue;
            }
            if (recordType.restFieldType != null && !types.isSameType(recordType.restFieldType, restFieldType)) {
                recordType.restFieldType = symTable.noType;
                dlog.error(recordTypeNode.pos,
                        DiagnosticErrorCode.
                        CANNOT_USE_TYPE_INCLUSION_WITH_MORE_THAN_ONE_OPEN_RECORD_WITH_DIFFERENT_REST_DESCRIPTOR_TYPES);
                return;
            }
            recordType.restFieldType = restFieldType;
            recordType.sealed = false;
        }

        if (recordType.restFieldType != null) {
            return;
        }
        recordType.restFieldType = symTable.noType;
    }

    private void resolveFields(BStructureType structureType, BLangStructureTypeNode structureTypeNode,
                               SymbolEnv typeDefEnv) {
        structureType.fields = structureTypeNode.fields.stream()
                .peek((BLangSimpleVariable field) -> defineNode(field, typeDefEnv))
                .filter(field -> field.symbol.type != symTable.semanticError) // filter out erroneous fields
                .map((BLangSimpleVariable field) -> {
                    field.symbol.isDefaultable = field.expr != null;
                    return new BField(names.fromIdNode(field.name), field.pos, field.symbol);
                })
                .collect(getFieldCollector());

        // Resolve referenced types and their fields of structural type
        resolveReferencedFields(structureTypeNode, typeDefEnv);

        // collect resolved type refs from structural type
        structureType.typeInclusions = new ArrayList<>();
        for (BLangType tRef : structureTypeNode.typeRefs) {
            BType type = tRef.type;
            structureType.typeInclusions.add(type);
        }

        // Add referenced fields of structural type
        defineReferencedFields(structureType, structureTypeNode, typeDefEnv);
    }

    private void defineReferencedFields(BStructureType structureType, BLangStructureTypeNode structureTypeNode,
                                        SymbolEnv typeDefEnv) {
        for (BLangSimpleVariable field : structureTypeNode.referencedFields) {
            defineNode(field, typeDefEnv);
            if (field.symbol.type == symTable.semanticError) {
                continue;
            }
            structureType.fields.put(field.name.value, new BField(names.fromIdNode(field.name), field.pos,
                    field.symbol));
        }
    }

    private void defineMembers(List<BLangNode> typeDefNodes, SymbolEnv pkgEnv) {
        for (BLangNode node : typeDefNodes) {
            if (node.getKind() == NodeKind.CLASS_DEFN) {
                defineMembersOfClassDef(pkgEnv, (BLangClassDefinition) node);
            } else if (node.getKind() == NodeKind.TYPE_DEFINITION) {
                defineMemberOfObjectTypeDef(pkgEnv, (BLangTypeDefinition) node);
            }
        }
    }

    private void defineMemberOfObjectTypeDef(SymbolEnv pkgEnv, BLangTypeDefinition node) {
        BLangTypeDefinition typeDef = node;
        if (typeDef.typeNode.getKind() == NodeKind.OBJECT_TYPE) {
            BObjectType objectType = (BObjectType) typeDef.symbol.type;

            if (objectType.mutableType != null) {
                // If this is an object type definition defined for an immutable type.
                // We skip defining methods here since they would either be defined already, or would be defined
                // later.
                return;
            }

            BLangObjectTypeNode objTypeNode = (BLangObjectTypeNode) typeDef.typeNode;
            SymbolEnv objMethodsEnv =
                    SymbolEnv.createObjectMethodsEnv(objTypeNode, (BObjectTypeSymbol) objTypeNode.symbol, pkgEnv);

            // Define the functions defined within the object
            defineObjectInitFunction(objTypeNode, objMethodsEnv);
            objTypeNode.functions.forEach(f -> {
                f.flagSet.add(Flag.FINAL); // Method's can't change once defined.
                f.setReceiver(ASTBuilderUtil.createReceiver(typeDef.pos, objectType));
                defineNode(f, objMethodsEnv);
            });

            Set<String> includedFunctionNames = new HashSet<>();
            // Add the attached functions of the referenced types to this object.
            // Here it is assumed that all the attached functions of the referred type are
            // resolved by the time we reach here. It is achieved by ordering the typeDefs
            // according to the precedence.
            for (BLangType typeRef : objTypeNode.typeRefs) {
                if (typeRef.type.tsymbol == null || typeRef.type.tsymbol.kind != SymbolKind.OBJECT) {
                    continue;
                }

                List<BAttachedFunction> functions = ((BObjectTypeSymbol) typeRef.type.tsymbol).attachedFuncs;
                for (BAttachedFunction function : functions) {
                    defineReferencedFunction(typeDef.pos, typeDef.flagSet, objMethodsEnv,
                            typeRef, function, includedFunctionNames, typeDef.symbol,
                            ((BLangObjectTypeNode) typeDef.typeNode).functions, node.internal);
                }
            }
        }
    }

    private void validateIntersectionTypeDefinitions(List<BLangTypeDefinition> typeDefNodes) {
        Set<BType> loggedTypes = new HashSet<>();

        for (BLangTypeDefinition typeDefNode : typeDefNodes) {
            BLangType typeNode = typeDefNode.typeNode;
            NodeKind kind = typeNode.getKind();
            if (kind == NodeKind.INTERSECTION_TYPE_NODE) {
                BType currentType = typeNode.type;

                if (currentType.tag != TypeTags.INTERSECTION) {
                    continue;
                }

                BIntersectionType intersectionType = (BIntersectionType) currentType;

                BType effectiveType = intersectionType.effectiveType;
                if (!loggedTypes.add(effectiveType)) {
                    continue;
                }

                boolean hasNonReadOnlyElement = false;
                for (BType constituentType : intersectionType.getConstituentTypes()) {
                    if (constituentType == symTable.readonlyType) {
                        continue;
                    }
                    // If constituent type is error, we have already validated error intersections.
                    if (!types.isSelectivelyImmutableType(constituentType, true)
                            && constituentType.tag != TypeTags.ERROR) {

                        hasNonReadOnlyElement = true;
                        break;
                    }
                }

                if (hasNonReadOnlyElement) {
                    dlog.error(typeDefNode.typeNode.pos, DiagnosticErrorCode.INVALID_INTERSECTION_TYPE, typeNode);
                    typeNode.type = symTable.semanticError;
                }

                continue;
            }

            BStructureType immutableType;
            BStructureType mutableType;

            if (kind == NodeKind.OBJECT_TYPE) {
                BObjectType currentType = (BObjectType) typeNode.type;
                mutableType = currentType.mutableType;
                if (mutableType == null) {
                    continue;
                }
                immutableType = currentType;
            } else if (kind == NodeKind.RECORD_TYPE) {
                BRecordType currentType = (BRecordType) typeNode.type;
                mutableType = currentType.mutableType;
                if (mutableType == null) {
                    continue;
                }
                immutableType = currentType;
            } else {
                continue;
            }

            if (!loggedTypes.add(immutableType)) {
                continue;
            }

            if (!types.isSelectivelyImmutableType(mutableType, true)) {
                dlog.error(typeDefNode.typeNode.pos, DiagnosticErrorCode.INVALID_INTERSECTION_TYPE, immutableType);
                typeNode.type = symTable.semanticError;
            }
        }
    }

    private void defineUndefinedReadOnlyTypes(List<BLangTypeDefinition> typeDefNodes, List<BLangNode> typeDefs,
                                              SymbolEnv pkgEnv) {
        // Any newly added typedefs are due to `T & readonly` typed fields. Once the fields are set for all
        // type-definitions we can revisit the newly added type-definitions and define the fields and members for them.
        populateImmutableTypeFieldsAndMembers(typeDefNodes, pkgEnv);

        // If all the fields of a structure are readonly or final, mark the structure type itself as readonly.
        // If the type is a `readonly object` validate if all fields are compatible.
        validateFieldsAndSetReadOnlyType(typeDefs, pkgEnv);

        defineReadOnlyInclusions(typeDefs, pkgEnv);
    }

    private void populateImmutableTypeFieldsAndMembers(List<BLangTypeDefinition> typeDefNodes, SymbolEnv pkgEnv) {
        int size = typeDefNodes.size();
        for (int i = 0; i < size; i++) {
            BLangTypeDefinition typeDef = typeDefNodes.get(i);
            NodeKind nodeKind = typeDef.typeNode.getKind();
            if (nodeKind == NodeKind.OBJECT_TYPE) {
                if (((BObjectType) typeDef.symbol.type).mutableType == null) {
                    continue;
                }
            } else if (nodeKind == NodeKind.RECORD_TYPE) {
                if (((BRecordType) typeDef.symbol.type).mutableType == null) {
                    continue;
                }
            } else {
                continue;
            }

            SymbolEnv typeDefEnv = SymbolEnv.createTypeEnv(typeDef.typeNode, typeDef.symbol.scope, pkgEnv);
            ImmutableTypeCloner.defineUndefinedImmutableFields(typeDef, types, typeDefEnv, symTable,
                                                               anonymousModelHelper, names);

            if (nodeKind != NodeKind.OBJECT_TYPE) {
                continue;
            }

            BObjectType immutableObjectType = (BObjectType) typeDef.symbol.type;
            BObjectType mutableObjectType = immutableObjectType.mutableType;

            ImmutableTypeCloner.defineObjectFunctions((BObjectTypeSymbol) immutableObjectType.tsymbol,
                                                      (BObjectTypeSymbol) mutableObjectType.tsymbol, names, symTable);
        }
    }

    private void validateFieldsAndSetReadOnlyType(List<BLangNode> typeDefNodes, SymbolEnv pkgEnv) {
        int origSize = typeDefNodes.size();
        for (int i = 0; i < origSize; i++) {
            BLangNode typeDefOrClass = typeDefNodes.get(i);
            if (typeDefOrClass.getKind() == NodeKind.CLASS_DEFN) {
                setReadOnlynessOfClassDef((BLangClassDefinition) typeDefOrClass, pkgEnv);
                continue;
            } else if (typeDefOrClass.getKind() != NodeKind.TYPE_DEFINITION) {
                continue;
            }

            BLangTypeDefinition typeDef = (BLangTypeDefinition) typeDefOrClass;
            BLangType typeNode = typeDef.typeNode;
            NodeKind nodeKind = typeNode.getKind();
            if (nodeKind != NodeKind.OBJECT_TYPE && nodeKind != NodeKind.RECORD_TYPE) {
                continue;
            }

            BTypeSymbol symbol = typeDef.symbol;
            BStructureType structureType = (BStructureType) symbol.type;

            if (Symbols.isFlagOn(structureType.flags, Flags.READONLY)) {
                if (structureType.tag != TypeTags.OBJECT) {
                    continue;
                }

                BObjectType objectType = (BObjectType) structureType;
                if (objectType.mutableType != null) {
                    // This is an object defined due to `T & readonly` like usage, thus validation has been done
                    // already.
                    continue;
                }

                Location pos = typeDef.pos;
                // We reach here for `readonly object`s.
                // We now validate if it is a valid `readonly object` - i.e., all the fields are compatible readonly
                // types.
                if (!types.isSelectivelyImmutableType(objectType, new HashSet<>())) {
                    dlog.error(pos, DiagnosticErrorCode.INVALID_READONLY_OBJECT_TYPE, objectType);
                    return;
                }

                SymbolEnv typeDefEnv = SymbolEnv.createTypeEnv(typeNode, symbol.scope, pkgEnv);
                for (BField field : objectType.fields.values()) {
                    BType type = field.type;

                    Set<Flag> flagSet;
                    if (typeNode.getKind() == NodeKind.OBJECT_TYPE) {
                        flagSet = ((BLangObjectTypeNode) typeNode).flagSet;
                    } else if (typeNode.getKind() == NodeKind.USER_DEFINED_TYPE) {
                        flagSet = ((BLangUserDefinedType) typeNode).flagSet;
                    } else {
                        flagSet = new HashSet<>();
                    }

                    if (!types.isInherentlyImmutableType(type)) {
                        field.type = field.symbol.type = ImmutableTypeCloner.getImmutableIntersectionType(
                                pos, types, (SelectivelyImmutableReferenceType) type, typeDefEnv, symTable,
                                anonymousModelHelper, names, flagSet);

                    }

                    field.symbol.flags |= Flags.READONLY;
                }
                continue;
            }

            if (nodeKind != NodeKind.RECORD_TYPE) {
                continue;
            }

            BRecordType recordType = (BRecordType) structureType;
            if (!recordType.sealed && recordType.restFieldType.tag != TypeTags.NEVER) {
                continue;
            }

            boolean allImmutableFields = true;

            Collection<BField> fields = structureType.fields.values();

            for (BField field : fields) {
                if (!Symbols.isFlagOn(field.symbol.flags, Flags.READONLY)) {
                    allImmutableFields = false;
                    break;
                }
            }

            if (allImmutableFields) {
                structureType.tsymbol.flags |= Flags.READONLY;
                structureType.flags |= Flags.READONLY;
            }
        }
    }

    private void defineReadOnlyInclusions(List<BLangNode> typeDefs, SymbolEnv pkgEnv) {
        for (BLangNode typeDef : typeDefs) {
            if (typeDef.getKind() != NodeKind.CLASS_DEFN) {
                continue;
            }

            BLangClassDefinition classDefinition = (BLangClassDefinition) typeDef;
            SymbolEnv typeDefEnv = SymbolEnv.createClassEnv(classDefinition, classDefinition.symbol.scope, pkgEnv);
            BObjectType objType = (BObjectType) ((BObjectTypeSymbol) classDefinition.symbol).type;
            defineReferencedClassFields(classDefinition, typeDefEnv, objType, true);

            SymbolEnv objMethodsEnv = SymbolEnv.createClassMethodsEnv(classDefinition,
                                                                      (BObjectTypeSymbol) classDefinition.symbol,
                                                                      pkgEnv);
            defineIncludedMethods(classDefinition, objMethodsEnv, true);
        }
    }

    private void setReadOnlynessOfClassDef(BLangClassDefinition classDef, SymbolEnv pkgEnv) {
        BObjectType objectType = (BObjectType) classDef.type;
        Location pos = classDef.pos;

        if (Symbols.isFlagOn(classDef.type.flags, Flags.READONLY)) {
            if (!types.isSelectivelyImmutableType(objectType, new HashSet<>())) {
                dlog.error(pos, DiagnosticErrorCode.INVALID_READONLY_OBJECT_TYPE, objectType);
                return;
            }

            ImmutableTypeCloner.markFieldsAsImmutable(classDef, pkgEnv, objectType, types, anonymousModelHelper,
                                                      symTable, names, pos);
        } else {
            Collection<BField> fields = objectType.fields.values();
            if (fields.isEmpty()) {
                return;
            }

            for (BField field : fields) {
                if (!Symbols.isFlagOn(field.symbol.flags, Flags.FINAL) ||
                        !Symbols.isFlagOn(field.type.flags, Flags.READONLY)) {
                    return;
                }
            }

            classDef.type.tsymbol.flags |= Flags.READONLY;
            classDef.type.flags |= Flags.READONLY;
        }
    }

    private void defineInvokableSymbol(BLangInvokableNode invokableNode, BInvokableSymbol funcSymbol,
                                       SymbolEnv invokableEnv) {
        invokableNode.symbol = funcSymbol;
        defineSymbol(invokableNode.name.pos, funcSymbol);
        invokableEnv.scope = funcSymbol.scope;
        defineInvokableSymbolParams(invokableNode, funcSymbol, invokableEnv);

        if (Symbols.isFlagOn(funcSymbol.type.tsymbol.flags, Flags.ISOLATED)) {
            funcSymbol.type.flags |= Flags.ISOLATED;
        }

        if (Symbols.isFlagOn(funcSymbol.type.tsymbol.flags, Flags.TRANSACTIONAL)) {
            funcSymbol.type.flags |= Flags.TRANSACTIONAL;
        }
    }

    private void defineInvokableSymbolParams(BLangInvokableNode invokableNode, BInvokableSymbol invokableSymbol,
                                             SymbolEnv invokableEnv) {
        boolean foundDefaultableParam = false;
        boolean foundIncludedRecordParam = false;
        List<BVarSymbol> paramSymbols = new ArrayList<>();
        Set<String> requiredParamNames = new HashSet<>();
        invokableNode.clonedEnv = invokableEnv.shallowClone();
        for (BLangSimpleVariable varNode : invokableNode.requiredParams) {
            boolean isDefaultableParam = varNode.expr != null;
            boolean isIncludedRecordParam = varNode.flagSet.contains(Flag.INCLUDED);
            defineNode(varNode, invokableEnv);
            if (isDefaultableParam) {
                foundDefaultableParam = true;
            } else if (isIncludedRecordParam) {
                foundIncludedRecordParam = true;
            }

            if (isDefaultableParam) {
                if (foundIncludedRecordParam) {
                    dlog.error(varNode.pos, DEFAULTABLE_PARAM_DEFINED_AFTER_INCLUDED_RECORD_PARAM);
                }
            } else if (!isIncludedRecordParam) {
                if (foundDefaultableParam) {
                    dlog.error(varNode.pos, REQUIRED_PARAM_DEFINED_AFTER_DEFAULTABLE_PARAM);
                } else if (foundIncludedRecordParam) {
                    dlog.error(varNode.pos, REQUIRED_PARAM_DEFINED_AFTER_INCLUDED_RECORD_PARAM);
                }
            }
            BVarSymbol symbol = varNode.symbol;
            if (varNode.expr != null) {
                symbol.flags |= Flags.OPTIONAL;
                symbol.isDefaultable = true;

                if (varNode.expr.getKind() == NodeKind.INFER_TYPEDESC_EXPR) {
                    symbol.flags |= Flags.INFER;
                }
            }
            if (varNode.flagSet.contains(Flag.INCLUDED)) {
                if (varNode.type.getKind() == TypeKind.RECORD) {
                    symbol.flags |= Flags.INCLUDED;
                    LinkedHashMap<String, BField> fields = ((BRecordType) varNode.type).fields;
                    for (String fieldName : fields.keySet()) {
                        BField field = fields.get(fieldName);
                        if (field.symbol.type.tag != TypeTags.NEVER) {
                            if (!requiredParamNames.add(fieldName)) {
                                dlog.error(varNode.pos, REDECLARED_SYMBOL, fieldName);
                            }
                        }
                    }
                } else {
                    dlog.error(varNode.typeNode.pos, EXPECTED_RECORD_TYPE_AS_INCLUDED_PARAMETER);
                }
            } else {
                requiredParamNames.add(symbol.name.value);
            }
            paramSymbols.add(symbol);
        }

        if (!invokableNode.desugaredReturnType) {
            symResolver.resolveTypeNode(invokableNode.returnTypeNode, invokableEnv);
        }
        invokableSymbol.params = paramSymbols;
        BType retType = invokableNode.returnTypeNode.type;
        invokableSymbol.retType = retType;

        symResolver.validateInferTypedescParams(invokableNode.pos, invokableNode.getParameters(), retType);

        // Create function type
        List<BType> paramTypes = paramSymbols.stream()
                .map(paramSym -> paramSym.type)
                .collect(Collectors.toList());

        BInvokableTypeSymbol functionTypeSymbol = Symbols.createInvokableTypeSymbol(SymTag.FUNCTION_TYPE,
                                                                                    invokableSymbol.flags,
                                                                                    env.enclPkg.symbol.pkgID,
                                                                                    invokableSymbol.type,
                                                                                    env.scope.owner, invokableNode.pos,
                                                                                    SOURCE);
        functionTypeSymbol.params = invokableSymbol.params;
        functionTypeSymbol.returnType = invokableSymbol.retType;

        BType restType = null;
        if (invokableNode.restParam != null) {
            defineNode(invokableNode.restParam, invokableEnv);
            invokableSymbol.restParam = invokableNode.restParam.symbol;
            functionTypeSymbol.restParam = invokableSymbol.restParam;
            restType = invokableSymbol.restParam.type;
        }
        invokableSymbol.type = new BInvokableType(paramTypes, restType, retType, null);
        invokableSymbol.type.tsymbol = functionTypeSymbol;
        invokableSymbol.type.tsymbol.type = invokableSymbol.type;
    }

    private void defineSymbol(Location pos, BSymbol symbol) {
        symbol.scope = new Scope(symbol);
        if (symResolver.checkForUniqueSymbol(pos, env, symbol)) {
            env.scope.define(symbol.name, symbol);
        }
    }

    public void defineSymbol(Location pos, BSymbol symbol, SymbolEnv env) {
        symbol.scope = new Scope(symbol);
        if (symResolver.checkForUniqueSymbol(pos, env, symbol)) {
            env.scope.define(symbol.name, symbol);
        }
    }

    /**
     * Define a symbol that is unique only for the current scope.
     *
     * @param pos Line number information of the source file
     * @param symbol Symbol to be defines
     * @param env Environment to define the symbol
     */
    public void defineShadowedSymbol(Location pos, BSymbol symbol, SymbolEnv env) {
        symbol.scope = new Scope(symbol);
        if (symResolver.checkForUniqueSymbolInCurrentScope(pos, env, symbol, symbol.tag)) {
            env.scope.define(symbol.name, symbol);
        }
    }

    public void defineTypeNarrowedSymbol(Location location, SymbolEnv targetEnv, BVarSymbol symbol,
                                         BType type, boolean isInternal) {
        if (symbol.owner.tag == SymTag.PACKAGE) {
            // Avoid defining shadowed symbol for global vars, since the type is not narrowed.
            return;
        }

        BVarSymbol varSymbol = createVarSymbol(symbol.flags, type, symbol.name, targetEnv, symbol.pos, isInternal);
        if (type.tag == TypeTags.INVOKABLE && type.tsymbol != null) {
            BInvokableTypeSymbol tsymbol = (BInvokableTypeSymbol) type.tsymbol;
            BInvokableSymbol invokableSymbol = (BInvokableSymbol) varSymbol;
            invokableSymbol.params = tsymbol.params;
            invokableSymbol.restParam = tsymbol.restParam;
            invokableSymbol.retType = tsymbol.returnType;
            invokableSymbol.flags = tsymbol.flags;
        }
        varSymbol.owner = symbol.owner;
        varSymbol.originalSymbol = symbol;
        defineShadowedSymbol(location, varSymbol, targetEnv);
    }

    private void defineSymbolWithCurrentEnvOwner(Location pos, BSymbol symbol) {
        symbol.scope = new Scope(env.scope.owner);
        if (symResolver.checkForUniqueSymbol(pos, env, symbol)) {
            env.scope.define(symbol.name, symbol);
        }
    }

    public BVarSymbol defineVarSymbol(Location pos, Set<Flag> flagSet, BType varType, Name varName,
                                      SymbolEnv env, boolean isInternal) {
        // Create variable symbol
        Scope enclScope = env.scope;
        BVarSymbol varSymbol = createVarSymbol(flagSet, varType, varName, env, pos, isInternal);
        boolean considerAsMemberSymbol = flagSet.contains(Flag.FIELD) || flagSet.contains(Flag.REQUIRED_PARAM) ||
                flagSet.contains(Flag.DEFAULTABLE_PARAM) || flagSet.contains(Flag.REST_PARAM) ||
                flagSet.contains(Flag.INCLUDED);

        if (considerAsMemberSymbol && !symResolver.checkForUniqueMemberSymbol(pos, env, varSymbol)) {
            varSymbol.type = symTable.semanticError;
        } else if (!considerAsMemberSymbol && !symResolver.checkForUniqueSymbol(pos, env, varSymbol)) {
            varSymbol.type = symTable.semanticError;
        }

        enclScope.define(varSymbol.name, varSymbol);
        return varSymbol;
    }

    public void defineExistingVarSymbolInEnv(BVarSymbol varSymbol, SymbolEnv env) {
        if (!symResolver.checkForUniqueSymbol(env, varSymbol)) {
            varSymbol.type = symTable.semanticError;
        }
        env.scope.define(varSymbol.name, varSymbol);
    }

    public BVarSymbol createVarSymbol(Set<Flag> flagSet, BType varType, Name varName, SymbolEnv env,
                                      Location pos, boolean isInternal) {
        return createVarSymbol(Flags.asMask(flagSet), varType, varName, env, pos, isInternal);
    }

    public BVarSymbol createVarSymbol(long flags, BType varType, Name varName, SymbolEnv env,
                                      Location location, boolean isInternal) {
        BVarSymbol varSymbol;
        if (varType.tag == TypeTags.INVOKABLE) {
            varSymbol = new BInvokableSymbol(SymTag.VARIABLE, flags, varName, env.enclPkg.symbol.pkgID, varType,
                                             env.scope.owner, location, isInternal ? VIRTUAL : getOrigin(varName));
            varSymbol.kind = SymbolKind.FUNCTION;
        } else {
            varSymbol = new BVarSymbol(flags, varName, env.enclPkg.symbol.pkgID, varType, env.scope.owner, location,
                                       isInternal ? VIRTUAL : getOrigin(varName));
            if (varType.tsymbol != null && Symbols.isFlagOn(varType.tsymbol.flags, Flags.CLIENT)) {
                varSymbol.tag = SymTag.ENDPOINT;
            }
        }
        return varSymbol;
    }

    private void defineObjectInitFunction(BLangObjectTypeNode object, SymbolEnv conEnv) {
        BLangFunction initFunction = object.initFunction;
        if (initFunction == null) {
            return;
        }

        //Set cached receiver to the init function
        initFunction.receiver = ASTBuilderUtil.createReceiver(object.pos, object.type);

        initFunction.attachedFunction = true;
        initFunction.flagSet.add(Flag.ATTACHED);
        defineNode(initFunction, conEnv);
    }

    private void defineClassInitFunction(BLangClassDefinition classDefinition, SymbolEnv conEnv) {
        BLangFunction initFunction = classDefinition.initFunction;
        if (initFunction == null) {
            return;
        }

        //Set cached receiver to the init function
        initFunction.receiver = ASTBuilderUtil.createReceiver(classDefinition.pos, classDefinition.type);

        initFunction.attachedFunction = true;
        initFunction.flagSet.add(Flag.ATTACHED);
        defineNode(initFunction, conEnv);
    }

    private void defineAttachedFunctions(BLangFunction funcNode, BInvokableSymbol funcSymbol,
                                         SymbolEnv invokableEnv, boolean isValidAttachedFunc) {
        BTypeSymbol typeSymbol = funcNode.receiver.type.tsymbol;

        // Check whether there exists a struct field with the same name as the function name.
        if (isValidAttachedFunc) {
            if (typeSymbol.tag == SymTag.OBJECT) {
                validateFunctionsAttachedToObject(funcNode, funcSymbol);
            } else if (typeSymbol.tag == SymTag.RECORD) {
                validateFunctionsAttachedToRecords(funcNode, funcSymbol);
            }
        }

        defineNode(funcNode.receiver, invokableEnv);
        funcSymbol.receiverSymbol = funcNode.receiver.symbol;
    }

    private void validateFunctionsAttachedToRecords(BLangFunction funcNode, BInvokableSymbol funcSymbol) {
        BInvokableType funcType = (BInvokableType) funcSymbol.type;
        BRecordTypeSymbol recordSymbol = (BRecordTypeSymbol) funcNode.receiver.type.tsymbol;

        recordSymbol.initializerFunc = new BAttachedFunction(
                names.fromIdNode(funcNode.name), funcSymbol, funcType, funcNode.pos);
    }

    private void validateFunctionsAttachedToObject(BLangFunction funcNode, BInvokableSymbol funcSymbol) {

        BInvokableType funcType = (BInvokableType) funcSymbol.type;
        BObjectTypeSymbol objectSymbol = (BObjectTypeSymbol) funcNode.receiver.type.tsymbol;
        BAttachedFunction attachedFunc;
        if (funcNode.getKind() == NodeKind.RESOURCE_FUNC) {
            attachedFunc = createResourceFunction(funcNode, funcSymbol, funcType);
        } else {
            attachedFunc = new BAttachedFunction(names.fromIdNode(funcNode.name), funcSymbol, funcType, funcNode.pos);
        }

        validateRemoteFunctionAttachedToObject(funcNode, objectSymbol);
        validateResourceFunctionAttachedToObject(funcNode, objectSymbol);

        // Check whether this attached function is a object initializer.
        if (!funcNode.objInitFunction) {
            objectSymbol.attachedFuncs.add(attachedFunc);
            return;
        }

        types.validateErrorOrNilReturn(funcNode, DiagnosticErrorCode.INVALID_OBJECT_CONSTRUCTOR);
        objectSymbol.initializerFunc = attachedFunc;
    }

    private BAttachedFunction createResourceFunction(BLangFunction funcNode, BInvokableSymbol funcSymbol,
                                                     BInvokableType funcType) {
        BLangResourceFunction resourceFunction = (BLangResourceFunction) funcNode;
        Name accessor = names.fromIdNode(resourceFunction.methodName);
        List<Name> resourcePath = resourceFunction.resourcePath.stream()
                .map(names::fromIdNode)
                .collect(Collectors.toList());

        List<BVarSymbol> pathParamSymbols = resourceFunction.pathParams.stream()
                .map(p -> {
                    p.symbol.kind = SymbolKind.PATH_PARAMETER;
                    return p.symbol;
                })
                .collect(Collectors.toList());

        BVarSymbol restPathParamSym = null;
        if (resourceFunction.restPathParam != null) {
            restPathParamSym = resourceFunction.restPathParam.symbol;
            restPathParamSym.kind = SymbolKind.PATH_REST_PARAMETER;
        }

        return new BResourceFunction(names.fromIdNode(funcNode.name), funcSymbol, funcType, resourcePath,
                                     accessor, pathParamSymbols, restPathParamSym, funcNode.pos);
    }

    private void validateRemoteFunctionAttachedToObject(BLangFunction funcNode, BObjectTypeSymbol objectSymbol) {
        if (!Symbols.isFlagOn(Flags.asMask(funcNode.flagSet), Flags.REMOTE)) {
            return;
        }
        funcNode.symbol.flags |= Flags.REMOTE;
        funcNode.symbol.flags |= Flags.PUBLIC;

        if (!isNetworkQualified(objectSymbol)) {
            this.dlog.error(funcNode.pos, DiagnosticErrorCode.REMOTE_FUNCTION_IN_NON_NETWORK_OBJECT);
        }
    }

    private boolean isNetworkQualified(BObjectTypeSymbol objectSymbol) {
        return Symbols.isFlagOn(objectSymbol.flags, Flags.CLIENT)
                || Symbols.isFlagOn(objectSymbol.flags, Flags.SERVICE);
    }

    private void validateResourceFunctionAttachedToObject(BLangFunction funcNode, BObjectTypeSymbol objectSymbol) {
        if (!Symbols.isFlagOn(Flags.asMask(funcNode.flagSet), Flags.RESOURCE)) {
            return;
        }
        funcNode.symbol.flags |= Flags.RESOURCE;

        if (!Symbols.isFlagOn(objectSymbol.flags, Flags.SERVICE)) {
            this.dlog.error(funcNode.pos, DiagnosticErrorCode.RESOURCE_FUNCTION_IN_NON_SERVICE_OBJECT);
        }
    }

    private StatementNode createAssignmentStmt(BLangSimpleVariable variable, BVarSymbol varSym, BSymbol fieldVar) {
        //Create LHS reference variable
        BLangSimpleVarRef varRef = (BLangSimpleVarRef) TreeBuilder.createSimpleVariableReferenceNode();
        varRef.pos = variable.pos;
        varRef.variableName = (BLangIdentifier) createIdentifier(fieldVar.name.getValue());
        varRef.pkgAlias = (BLangIdentifier) TreeBuilder.createIdentifierNode();
        varRef.symbol = fieldVar;
        varRef.type = fieldVar.type;

        //Create RHS variable reference
        BLangSimpleVarRef exprVar = (BLangSimpleVarRef) TreeBuilder.createSimpleVariableReferenceNode();
        exprVar.pos = variable.pos;
        exprVar.variableName = (BLangIdentifier) createIdentifier(varSym.name.getValue());
        exprVar.pkgAlias = (BLangIdentifier) TreeBuilder.createIdentifierNode();
        exprVar.symbol = varSym;
        exprVar.type = varSym.type;

        //Create assignment statement
        BLangAssignment assignmentStmt = (BLangAssignment) TreeBuilder.createAssignmentNode();
        assignmentStmt.expr = exprVar;
        assignmentStmt.pos = variable.pos;
        assignmentStmt.setVariable(varRef);
        return assignmentStmt;
    }

    private IdentifierNode createIdentifier(String value) {
        IdentifierNode node = TreeBuilder.createIdentifierNode();
        if (value != null) {
            node.setValue(value);
        }
        return node;
    }

    private boolean validateFuncReceiver(BLangFunction funcNode) {
        if (funcNode.receiver == null) {
            return true;
        }

        if (funcNode.receiver.type == null) {
            funcNode.receiver.type = symResolver.resolveTypeNode(funcNode.receiver.typeNode, env);
        }
        if (funcNode.receiver.type.tag == TypeTags.SEMANTIC_ERROR) {
            return true;
        }

        if (funcNode.receiver.type.tag == TypeTags.OBJECT
                && !this.env.enclPkg.symbol.pkgID.equals(funcNode.receiver.type.tsymbol.pkgID)) {
            dlog.error(funcNode.receiver.pos, DiagnosticErrorCode.FUNC_DEFINED_ON_NON_LOCAL_TYPE,
                    funcNode.name.value, funcNode.receiver.type.toString());
            return false;
        }
        return true;
    }

    private Name getFuncSymbolName(BLangFunction funcNode) {
        if (funcNode.receiver != null) {
            return names.fromString(Symbols.getAttachedFuncSymbolName(
                    funcNode.receiver.type.tsymbol.name.value, funcNode.name.value));
        }
        return names.fromIdNode(funcNode.name);
    }

    private Name getFieldSymbolName(BLangSimpleVariable receiver, BLangSimpleVariable variable) {
        return names.fromString(Symbols.getAttachedFuncSymbolName(
                receiver.type.tsymbol.name.value, variable.name.value));
    }

    private MarkdownDocAttachment getMarkdownDocAttachment(BLangMarkdownDocumentation docNode) {
        if (docNode == null) {
            return new MarkdownDocAttachment(0);
        }
        MarkdownDocAttachment docAttachment = new MarkdownDocAttachment(docNode.getParameters().size());
        docAttachment.description = docNode.getDocumentation();

        for (BLangMarkdownParameterDocumentation p : docNode.getParameters()) {
            docAttachment.parameters.add(new MarkdownDocAttachment.Parameter(p.parameterName.value,
                                                                             p.getParameterDocumentation()));
        }

        docAttachment.returnValueDescription = docNode.getReturnParameterDocumentation();
        BLangMarkDownDeprecationDocumentation deprecatedDocs = docNode.getDeprecationDocumentation();

        if (deprecatedDocs == null) {
            return docAttachment;
        }

        docAttachment.deprecatedDocumentation = deprecatedDocs.getDocumentation();

        BLangMarkDownDeprecatedParametersDocumentation deprecatedParamsDocs =
                docNode.getDeprecatedParametersDocumentation();

        if (deprecatedParamsDocs == null) {
            return docAttachment;
        }

        for (BLangMarkdownParameterDocumentation param : deprecatedParamsDocs.getParameters()) {
            docAttachment.deprecatedParams.add(
                    new MarkdownDocAttachment.Parameter(param.parameterName.value, param.getParameterDocumentation()));
        }

        return docAttachment;
    }

    private void resolveReferencedFields(BLangStructureTypeNode structureTypeNode, SymbolEnv typeDefEnv) {
        Set<BSymbol> referencedTypes = new HashSet<>();
        List<BLangType> invalidTypeRefs = new ArrayList<>();
        // Get the inherited fields from the type references

        Map<String, BLangSimpleVariable> fieldNames = new HashMap<>(structureTypeNode.fields.size());
        for (BLangSimpleVariable fieldVariable : structureTypeNode.fields) {
            fieldNames.put(fieldVariable.name.value, fieldVariable);
        }

        structureTypeNode.referencedFields = structureTypeNode.typeRefs.stream().flatMap(typeRef -> {
            BType referredType = symResolver.resolveTypeNode(typeRef, typeDefEnv);
            if (referredType == symTable.semanticError) {
                return Stream.empty();
            }

            // Check for duplicate type references
            if (!referencedTypes.add(referredType.tsymbol)) {
                dlog.error(typeRef.pos, DiagnosticErrorCode.REDECLARED_TYPE_REFERENCE, typeRef);
                return Stream.empty();
            }

            int referredTypeTag = referredType.tag;
            if (structureTypeNode.type.tag == TypeTags.OBJECT) {
                if (referredTypeTag != TypeTags.OBJECT) {
                    DiagnosticErrorCode errorCode = DiagnosticErrorCode.INCOMPATIBLE_TYPE_REFERENCE;

                    if (referredTypeTag == TypeTags.INTERSECTION &&
                            isReadOnlyAndObjectIntersection((BIntersectionType) referredType)) {
                        errorCode = DiagnosticErrorCode.INVALID_READ_ONLY_TYPEDESC_INCLUSION_IN_OBJECT_TYPEDESC;
                    }

                    dlog.error(typeRef.pos, errorCode, typeRef);
                    invalidTypeRefs.add(typeRef);
                    return Stream.empty();
                }

                BObjectType objectType = (BObjectType) referredType;
                if (structureTypeNode.type.tsymbol.owner != referredType.tsymbol.owner) {
                    for (BField field : objectType.fields.values()) {
                        if (!Symbols.isPublic(field.symbol)) {
                            dlog.error(typeRef.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPE_REFERENCE_NON_PUBLIC_MEMBERS,
                                       typeRef);
                            invalidTypeRefs.add(typeRef);
                            return Stream.empty();
                        }
                    }

                    for (BAttachedFunction func : ((BObjectTypeSymbol) objectType.tsymbol).attachedFuncs) {
                        if (!Symbols.isPublic(func.symbol)) {
                            dlog.error(typeRef.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPE_REFERENCE_NON_PUBLIC_MEMBERS,
                                       typeRef);
                            invalidTypeRefs.add(typeRef);
                            return Stream.empty();
                        }
                    }
                }
            }

            if (structureTypeNode.type.tag == TypeTags.RECORD && referredTypeTag != TypeTags.RECORD) {
                dlog.error(typeRef.pos, DiagnosticErrorCode.INCOMPATIBLE_RECORD_TYPE_REFERENCE, typeRef);
                invalidTypeRefs.add(typeRef);
                return Stream.empty();
            }

            // Here it is assumed that all the fields of the referenced types are resolved
            // by the time we reach here. It is achieved by ordering the typeDefs according
            // to the precedence.
            // Default values of fields are not inherited.
            return ((BStructureType) referredType).fields.values().stream().filter(f -> {
                if (fieldNames.containsKey(f.name.value)) {
                    BLangSimpleVariable existingVariable = fieldNames.get(f.name.value);
                    if (existingVariable.flagSet.contains(Flag.PUBLIC) !=
                            Symbols.isFlagOn(f.symbol.flags, Flags.PUBLIC)) {
                        dlog.error(existingVariable.pos,
                                DiagnosticErrorCode.MISMATCHED_VISIBILITY_QUALIFIERS_IN_OBJECT_FIELD,
                                existingVariable.name.value);
                    }
                    return !types.isAssignable(existingVariable.type, f.type);
                }
                return true;
            }).map(field -> {
                BLangSimpleVariable var = ASTBuilderUtil.createVariable(typeRef.pos, field.name.value, field.type);
                var.flagSet = field.symbol.getFlags();
                return var;
            });
        }).collect(Collectors.toList());
        structureTypeNode.typeRefs.removeAll(invalidTypeRefs);
    }

    private void defineReferencedFunction(Location location, Set<Flag> flagSet, SymbolEnv objEnv,
                                          BLangType typeRef, BAttachedFunction referencedFunc,
                                          Set<String> includedFunctionNames, BTypeSymbol typeDefSymbol,
                                          List<BLangFunction> declaredFunctions, boolean isInternal) {
        String referencedFuncName = referencedFunc.funcName.value;
        Name funcName = names.fromString(
                Symbols.getAttachedFuncSymbolName(typeDefSymbol.name.value, referencedFuncName));
        BSymbol matchingObjFuncSym = symResolver.lookupSymbolInMainSpace(objEnv, funcName);

        if (matchingObjFuncSym != symTable.notFoundSymbol) {
            if (!includedFunctionNames.add(referencedFuncName)) {
                dlog.error(typeRef.pos, DiagnosticErrorCode.REDECLARED_SYMBOL, referencedFuncName);
                return;
            }

            if (!hasSameFunctionSignature((BInvokableSymbol) matchingObjFuncSym, referencedFunc.symbol)) {
                BLangFunction matchingFunc = findFunctionBySymbol(declaredFunctions, matchingObjFuncSym);
                Location methodPos = matchingFunc != null ? matchingFunc.pos : typeRef.pos;
                dlog.error(methodPos, DiagnosticErrorCode.REFERRED_FUNCTION_SIGNATURE_MISMATCH,
                           getCompleteFunctionSignature(referencedFunc.symbol),
                           getCompleteFunctionSignature((BInvokableSymbol) matchingObjFuncSym));
            }

            if (Symbols.isFunctionDeclaration(matchingObjFuncSym) && Symbols.isFunctionDeclaration(
                    referencedFunc.symbol) && !types.isAssignable(matchingObjFuncSym.type, referencedFunc.type)) {
                BLangFunction matchingFunc = findFunctionBySymbol(declaredFunctions, matchingObjFuncSym);
                Location methodPos = matchingFunc != null ? matchingFunc.pos : typeRef.pos;
                dlog.error(methodPos, DiagnosticErrorCode.REDECLARED_FUNCTION_FROM_TYPE_REFERENCE,
                        referencedFunc.funcName, typeRef);
            }
            return;
        }

        if (Symbols.isPrivate(referencedFunc.symbol)) {
            // we should not copy private functions.
            return;
        }

        // If not, define the function symbol within the object.
        // Take a copy of the symbol, with the new name, and the package ID same as the object type.
        BInvokableSymbol funcSymbol = ASTBuilderUtil.duplicateFunctionDeclarationSymbol(referencedFunc.symbol,
                typeDefSymbol, funcName, typeDefSymbol.pkgID, typeRef.pos, getOrigin(funcName));
        defineSymbol(typeRef.pos, funcSymbol, objEnv);

        // Create and define the parameters and receiver. This should be done after defining the function symbol.
        SymbolEnv funcEnv = SymbolEnv.createFunctionEnv(null, funcSymbol.scope, objEnv);
        funcSymbol.params.forEach(param -> defineSymbol(typeRef.pos, param, funcEnv));
        if (funcSymbol.restParam != null) {
            defineSymbol(typeRef.pos, funcSymbol.restParam, funcEnv);
        }
        funcSymbol.receiverSymbol =
                defineVarSymbol(location, flagSet, typeDefSymbol.type, Names.SELF, funcEnv, isInternal);

        // Cache the function symbol.
        BAttachedFunction attachedFunc;
        if (referencedFunc instanceof BResourceFunction) {
            BResourceFunction resourceFunction = (BResourceFunction) referencedFunc;
            attachedFunc = new BResourceFunction(referencedFunc.funcName,
                    funcSymbol, (BInvokableType) funcSymbol.type, resourceFunction.resourcePath,
                    resourceFunction.accessor, resourceFunction.pathParams, resourceFunction.restPathParam,
                    referencedFunc.pos);
        } else {
            attachedFunc = new BAttachedFunction(referencedFunc.funcName, funcSymbol, (BInvokableType) funcSymbol.type,
                    referencedFunc.pos);
        }

        ((BObjectTypeSymbol) typeDefSymbol).attachedFuncs.add(attachedFunc);
        ((BObjectTypeSymbol) typeDefSymbol).referencedFunctions.add(attachedFunc);
    }

    private BLangFunction findFunctionBySymbol(List<BLangFunction> declaredFunctions, BSymbol symbol) {
        for (BLangFunction fn : declaredFunctions) {
            if (fn.symbol == symbol) {
                return fn;
            }
        }
        return null;
    }

    private boolean hasSameFunctionSignature(BInvokableSymbol attachedFuncSym, BInvokableSymbol referencedFuncSym) {
        if (!hasSameVisibilityModifier(referencedFuncSym.flags, attachedFuncSym.flags)) {
            return false;
        }

        if (!types.isAssignable(attachedFuncSym.type, referencedFuncSym.type)) {
            return false;
        }

        List<BVarSymbol> params = referencedFuncSym.params;
        for (int i = 0; i < params.size(); i++) {
            BVarSymbol referencedFuncParam = params.get(i);
            BVarSymbol attachedFuncParam = attachedFuncSym.params.get(i);
            if (!referencedFuncParam.name.value.equals(attachedFuncParam.name.value) ||
                    !hasSameVisibilityModifier(referencedFuncParam.flags, attachedFuncParam.flags)) {
                return false;
            }
        }

        if (referencedFuncSym.restParam != null && attachedFuncSym.restParam != null) {
            return referencedFuncSym.restParam.name.value.equals(attachedFuncSym.restParam.name.value);
        }

        return referencedFuncSym.restParam == null && attachedFuncSym.restParam == null;
    }

    private boolean hasSameVisibilityModifier(long flags1, long flags2) {
        var xorOfFlags = flags1 ^ flags2;
        return ((xorOfFlags & Flags.PUBLIC) != Flags.PUBLIC) && ((xorOfFlags & Flags.PRIVATE) != Flags.PRIVATE);
    }

    private String getCompleteFunctionSignature(BInvokableSymbol funcSymbol) {
        StringBuilder signatureBuilder = new StringBuilder();
        StringJoiner paramListBuilder = new StringJoiner(", ", "(", ")");

        String visibilityModifier = "";
        if (Symbols.isPublic(funcSymbol)) {
            visibilityModifier = "public ";
        } else if (Symbols.isPrivate(funcSymbol)) {
            visibilityModifier = "private ";
        }

        signatureBuilder.append(visibilityModifier).append("function ")
                .append(funcSymbol.name.value.split("\\.")[1]);

        funcSymbol.params.forEach(param -> paramListBuilder.add(
                (Symbols.isPublic(param) ? "public " : "") + param.type.toString() + " " + param.name.value));

        if (funcSymbol.restParam != null) {
            paramListBuilder.add(((BArrayType) funcSymbol.restParam.type).eType.toString() + "... " +
                                         funcSymbol.restParam.name.value);
        }

        signatureBuilder.append(paramListBuilder.toString());

        if (funcSymbol.retType != symTable.nilType) {
            signatureBuilder.append(" returns ").append(funcSymbol.retType.toString());
        }

        return signatureBuilder.toString();
    }

    private BPackageSymbol dupPackageSymbolAndSetCompUnit(BPackageSymbol originalSymbol, Name compUnit) {
        BPackageSymbol copy = new BPackageSymbol(originalSymbol.pkgID, originalSymbol.owner, originalSymbol.flags,
                                                 originalSymbol.pos, originalSymbol.origin);
        copy.initFunctionSymbol = originalSymbol.initFunctionSymbol;
        copy.startFunctionSymbol = originalSymbol.startFunctionSymbol;
        copy.stopFunctionSymbol = originalSymbol.stopFunctionSymbol;
        copy.testInitFunctionSymbol = originalSymbol.testInitFunctionSymbol;
        copy.testStartFunctionSymbol = originalSymbol.testStartFunctionSymbol;
        copy.testStopFunctionSymbol = originalSymbol.testStopFunctionSymbol;
        copy.packageFile = originalSymbol.packageFile;
        copy.compiledPackage = originalSymbol.compiledPackage;
        copy.entryPointExists = originalSymbol.entryPointExists;
        copy.scope = originalSymbol.scope;
        copy.owner = originalSymbol.owner;
        copy.compUnit = compUnit;
        return copy;
    }

    private boolean isSameImport(BLangImportPackage importPkgNode, BPackageSymbol importSymbol) {
        if (!importPkgNode.orgName.value.equals(importSymbol.pkgID.orgName.value)) {
            return false;
        }

        BLangIdentifier pkgName = importPkgNode.pkgNameComps.get(importPkgNode.pkgNameComps.size() - 1);
        return pkgName.value.equals(importSymbol.pkgID.name.value);
    }

    private void resolveAndSetFunctionTypeFromRHSLambda(BLangVariable variable, SymbolEnv env) {
        BLangFunction function = ((BLangLambdaFunction) variable.expr).function;
        BInvokableType invokableType = (BInvokableType) symResolver.createInvokableType(function.getParameters(),
                                                                                        function.restParam,
                                                                                        function.returnTypeNode,
                                                                                        Flags.asMask(variable.flagSet),
                                                                                        env,
                                                                                        function.pos);

        if (function.flagSet.contains(Flag.ISOLATED)) {
            invokableType.flags |= Flags.ISOLATED;
            invokableType.tsymbol.flags |= Flags.ISOLATED;
        }

        if (function.flagSet.contains(Flag.TRANSACTIONAL)) {
            invokableType.flags |= Flags.TRANSACTIONAL;
            invokableType.tsymbol.flags |= Flags.TRANSACTIONAL;
        }

        variable.type = invokableType;
    }

    private SymbolOrigin getOrigin(Name name, Set<Flag> flags) {
        if ((flags.contains(Flag.ANONYMOUS) && (flags.contains(Flag.SERVICE) || flags.contains(Flag.CLASS)))
                || missingNodesHelper.isMissingNode(name)) {
            return VIRTUAL;
        }
        return SOURCE;
    }

    private SymbolOrigin getOrigin(Name name) {
        return getOrigin(name.value);
    }

    private SymbolOrigin getOrigin(String name) {
        if (missingNodesHelper.isMissingNode(name)) {
            return VIRTUAL;
        }
        return SOURCE;
    }

    private boolean isInvalidIncludedTypeInClass(BType includedType) {
        int tag = includedType.tag;

        if (tag == TypeTags.OBJECT) {
            return false;
        }

        if (tag != TypeTags.INTERSECTION) {
            return true;
        }

        for (BType constituentType : ((BIntersectionType) includedType).getConstituentTypes()) {
            int constituentTypeTag = constituentType.tag;

            if (constituentTypeTag != TypeTags.OBJECT && constituentTypeTag != TypeTags.READONLY) {
                return true;
            }
        }
        return false;
    }

    private boolean isImmutable(BObjectType objectType) {
        if (Symbols.isFlagOn(objectType.flags, Flags.READONLY)) {
            return true;
        }

        Collection<BField> fields = objectType.fields.values();
        if (fields.isEmpty()) {
            return false;
        }

        for (BField field : fields) {
            if (!Symbols.isFlagOn(field.symbol.flags, Flags.FINAL) ||
                    !Symbols.isFlagOn(field.type.flags, Flags.READONLY)) {
                return false;
            }
        }

        return true;
    }

    private boolean isReadOnlyAndObjectIntersection(BIntersectionType referredType) {
        BType effectiveType = referredType.effectiveType;

        if (effectiveType.tag != TypeTags.OBJECT || !Symbols.isFlagOn(effectiveType.flags, Flags.READONLY)) {
            return false;
        }

        for (BType constituentType : referredType.getConstituentTypes()) {
            if (constituentType.tag == TypeTags.READONLY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Holds imports that are resolved and unresolved.
     */
    public static class ImportResolveHolder {
        public BLangImportPackage resolved;
        public List<BLangImportPackage> unresolved;

        public ImportResolveHolder() {
            this.unresolved = new ArrayList<>();
        }

        public ImportResolveHolder(BLangImportPackage resolved) {
            this.resolved = resolved;
            this.unresolved = new ArrayList<>();
        }
    }

    /**
     * Used to store location data for encountered unknown types in `checkErrors` method.
     *
     * @since 0.985.0
     */
    class LocationData {

        private String name;
        private int row;
        private int column;

        LocationData(String name, int row, int column) {
            this.name = name;
            this.row = row;
            this.column = column;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LocationData that = (LocationData) o;
            return row == that.row &&
                    column == that.column &&
                    name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, row, column);
        }
    }
}
