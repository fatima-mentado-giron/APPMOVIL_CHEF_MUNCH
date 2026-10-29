import {before,after,beforeEach,test} from 'node:test';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,updateDoc,collection,query,where,getDocs,serverTimestamp,Timestamp} from 'firebase/firestore';
import {ref,uploadBytes,getMetadata} from 'firebase/storage';

let env;
const id='pedido-uuid-0000000000000000001';
const order=(extra={})=>({id,clienteUid:'cliente1',repartidorUid:'',entregaEstado:'SIN_ASIGNAR',destinoLat:null,destinoLng:null,referencias:'Puerta verde',kitNombre:'Date Night',platillo:'Pasta',porciones:2,preferencias:'',direccion:'Calle 1',fecha:'2026-11-01',horario:'12:00',metodoPago:'Pago al recibir',comprobanteUri:null,estado:'PAGO_CONFIRMADO',fechaCreacion:Date.now(),...extra});
const db=(uid,claims={})=>env.authenticatedContext(uid,claims).firestore();
const pedido=(uid,claims={})=>doc(db(uid,claims),'pedidos',id);
async function seed(extra={}) {
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'pedidos',id),order(extra));
  await setDoc(doc(c.firestore(),'repartidores','repa1'),{nombre:'Repartidor uno',activo:true});
 });
}
before(async()=>{
 env=await initializeTestEnvironment({projectId:'demo-munch',firestore:{rules:readFileSync('firebase/firestore.rules','utf8')},storage:{rules:readFileSync('firebase/storage.rules','utf8')}});
});
beforeEach(async()=>{await env.clearFirestore();await env.clearStorage();});
after(async()=>{await env?.cleanup();});
test('cliente crea solo su pedido con UUID y no modifica pagos, roles ni asignación',async()=>{
 await assertSucceeds(setDoc(pedido('cliente1'),order()));
 await assertFails(updateDoc(pedido('cliente1'),{estado:'ENTREGADO_A_CLIENTE'}));
 await assertFails(updateDoc(pedido('cliente1'),{repartidorUid:'cliente1'}));
 await assertFails(setDoc(doc(db('cliente1'),'repartidores','cliente1'),{activo:true,admin:true}));
 await assertFails(setDoc(doc(db('cliente1'),'roles','cliente1'),{admin:true}));
 await assertFails(setDoc(doc(db('cliente1'),'pedidos','corto'),order({id:'corto'})));
 await assertFails(setDoc(doc(db('cliente1'),'pedidos',id+'x'),order({id:id+'x',clienteUid:'otro'})));
});
test('acceso por propietario y consulta filtrada; un cliente ajeno no ve pedidos ni seguimiento',async()=>{
 await seed();
 await assertSucceeds(getDoc(pedido('cliente1')));
 await assertFails(getDoc(pedido('cliente2')));
 await assertSucceeds(getDocs(query(collection(db('cliente1'),'pedidos'),where('clienteUid','==','cliente1'))));
 await assertFails(getDocs(collection(db('cliente1'),'pedidos')));
 await assertFails(getDoc(doc(db('cliente2'),'pedidos',id,'seguimiento','actual')));
});
test('solo admin asigna a repartidor registrado y no durante recorrido',async()=>{
 await seed();
 await assertFails(updateDoc(pedido('repa1',{repartidor:true}),{repartidorUid:'repa1',entregaEstado:'ASIGNADA'}));
 await assertFails(updateDoc(pedido('administrador',{admin:true}),{repartidorUid:'desconocido',entregaEstado:'ASIGNADA'}));
 await assertSucceeds(updateDoc(pedido('administrador',{admin:true}),{repartidorUid:'repa1',entregaEstado:'ASIGNADA'}));
 await assertSucceeds(updateDoc(pedido('repa1',{repartidor:true}),{entregaEstado:'EN_CURSO',estado:'ENTREGADO_A_REPARTIDOR'}));
 await assertFails(updateDoc(pedido('administrador',{admin:true}),{repartidorUid:'otro',entregaEstado:'ASIGNADA'}));
});
test('repartidor solo ve asignados y respeta inicio, finalización y campos permitidos',async()=>{
 await seed({repartidorUid:'repa1',entregaEstado:'ASIGNADA'});
 const r=pedido('repa1',{repartidor:true});
 await assertSucceeds(getDoc(r));
 await assertFails(getDoc(pedido('repa2',{repartidor:true})));
 await assertFails(getDoc(pedido('repa1')));
 await assertFails(updateDoc(r,{estado:'ENTREGADO_A_CLIENTE',entregaEstado:'ENTREGADA'}));
 await assertSucceeds(updateDoc(r,{estado:'ENTREGADO_A_REPARTIDOR',entregaEstado:'EN_CURSO'}));
 await assertFails(updateDoc(r,{direccion:'Otra dirección'}));
 await assertSucceeds(updateDoc(r,{estado:'ENTREGADO_A_CLIENTE',entregaEstado:'ENTREGADA'}));
 await assertFails(updateDoc(r,{estado:'ENTREGADO_A_REPARTIDOR',entregaEstado:'EN_CURSO'}));
});
test('ubicación exige asignación, entrega activa, precisión y fecha reciente',async()=>{
 await seed({repartidorUid:'repa1',entregaEstado:'ASIGNADA'});
 const fix=uid=>doc(db(uid,{repartidor:true}),'pedidos',id,'seguimiento','actual');
 const data=()=>({latitud:19.4,longitud:-99.1,precision:20,medidoEn:Timestamp.now(),actualizadoEn:serverTimestamp()});
 await assertFails(setDoc(fix('repa1'),data()));
 await assertSucceeds(updateDoc(pedido('repa1',{repartidor:true}),{estado:'ENTREGADO_A_REPARTIDOR',entregaEstado:'EN_CURSO'}));
 await assertSucceeds(setDoc(fix('repa1'),data()));
 await assertSucceeds(getDoc(doc(db('cliente1'),'pedidos',id,'seguimiento','actual')));
 await assertFails(setDoc(fix('repa2'),data()));
 await assertFails(setDoc(fix('repa1'),{...data(),latitud:95}));
 await assertFails(setDoc(fix('repa1'),{...data(),medidoEn:Timestamp.fromMillis(0)}));
 await assertSucceeds(updateDoc(pedido('repa1',{repartidor:true}),{estado:'EN_PREPARACION',entregaEstado:'CANCELADA'}));
 await assertFails(setDoc(fix('repa1'),data()));
});
test('pedidos antiguos siguen legibles por admin sin conceder propiedad a cualquiera',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'pedidos','DEMO-1234'),{kitNombre:'Family',estado:'EN_PREPARACION'}));
 await assertSucceeds(getDoc(doc(db('admin',{admin:true}),'pedidos','DEMO-1234')));
 await assertFails(getDoc(doc(db('cliente1'),'pedidos','DEMO-1234')));
});
test('videos: solo admin sube archivos; metadatos no publicados no son visibles al cliente',async()=>{
 const v={titulo:'Pasta',kit:'Date Night',url:'https://firebasestorage.googleapis.com/v0/b/demo/o/test?alt=media&token=test',storagePath:'videos/v1/version1',publicado:false,bytes:4,actualizadoEn:serverTimestamp()};
 await assertFails(setDoc(doc(db('cliente1'),'videos','v1'),v));
 await assertSucceeds(setDoc(doc(db('admin',{admin:true}),'videos','v1'),v));
 await assertFails(getDoc(doc(db('cliente1'),'videos','v1')));
 await assertFails(uploadBytes(ref(env.authenticatedContext('cliente1').storage(),'videos/v1/version1'),new Uint8Array([0,1,2,3]),{contentType:'video/mp4'}));
 await assertFails(uploadBytes(ref(env.authenticatedContext('admin',{admin:true}).storage(),'videos/v1/version1'),new Uint8Array([0,1]),{contentType:'text/plain'}));
 await assertSucceeds(uploadBytes(ref(env.authenticatedContext('admin',{admin:true}).storage(),'videos/v1/version1'),new Uint8Array([0,1,2,3]),{contentType:'video/mp4'}));
 await assertFails(getMetadata(ref(env.authenticatedContext('cliente1').storage(),'videos/v1/version1')));
 await assertSucceeds(updateDoc(doc(db('admin',{admin:true}),'videos','v1'),{publicado:true}));
 await assertSucceeds(getDoc(doc(db('cliente1'),'videos','v1')));
 await assertSucceeds(getMetadata(ref(env.authenticatedContext('cliente1').storage(),'videos/v1/version1')));
 await assertFails(getMetadata(ref(env.unauthenticatedContext().storage(),'videos/v1/version1')));
 await assertSucceeds(updateDoc(doc(db('admin',{admin:true}),'videos','v1'),{publicado:false}));
 await assertFails(getMetadata(ref(env.authenticatedContext('cliente1').storage(),'videos/v1/version1')));
});
