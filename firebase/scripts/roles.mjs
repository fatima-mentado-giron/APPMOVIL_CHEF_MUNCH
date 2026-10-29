// Explicit operator command only. Never called by builds, tests, or mobile apps.
// Uses Application Default Credentials from a trusted workstation, not a key in Git.
import {initializeApp,applicationDefault} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
import {getFirestore} from 'firebase-admin/firestore';
const [uid,role,nombre]=process.argv.slice(2);
if (!uid || !['admin','repartidor','revocar-admin','revocar-repartidor'].includes(role)) {
 console.error('Uso: npm run roles -- UID admin|repartidor|revocar-admin|revocar-repartidor [nombre]');
 process.exit(1);
}
if (!process.env.GOOGLE_CLOUD_PROJECT) throw Error('Define GOOGLE_CLOUD_PROJECT explícitamente');
initializeApp({credential:applicationDefault(),projectId:process.env.GOOGLE_CLOUD_PROJECT});
const user=await getAuth().getUser(uid);
const claims={...user.customClaims};
const revocar=role.startsWith('revocar-');
const key=role.replace('revocar-','');
if (revocar) delete claims[key]; else claims[key]=true;
await getAuth().setCustomUserClaims(uid,claims);
if (key==='repartidor') await getFirestore().collection('repartidores').doc(uid).set({nombre:nombre||user.displayName||user.email||uid,activo:!revocar});
console.log(`Rol ${role} aplicado a ${uid}. Cierra y abre sesión en el teléfono para renovar el token.`);
