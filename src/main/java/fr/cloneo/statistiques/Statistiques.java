package fr.cloneo.statistiques;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;


public class Statistiques {
	//CONSTANTES
	private static Class<?> dictionaryType = Map.class;
	private static Class<?> collectionType = Collection.class;
	private static Class<?> stringType = String.class;
	private static Class<?> dateType = Date.class;
	
	private Queue<Object> aTraiter = new SetQueue();

	private Map<Class<?>, Set<Object>> classement = new HashMap<>();
	public Map<Class<?>, Set<Object>> getClassement(){
		return classement;
	}
	
	private Object objetSource;
	public Object getObjetSource(){
		return objetSource;
	}
	
	public Statistiques(Object object) throws IllegalArgumentException, IllegalAccessException, NoSuchMethodException, SecurityException, InstantiationException, InvocationTargetException {
		objetSource = object;
		aTraiter.add(objetSource);
		classe();
	}
	
	private void classe() throws IllegalArgumentException, IllegalAccessException, NoSuchMethodException, SecurityException, InstantiationException, InvocationTargetException {
		while(!aTraiter.isEmpty()){
			traite(aTraiter.poll());
		}
	}

	private void traite(Object obj) throws IllegalArgumentException, IllegalAccessException, NoSuchMethodException, SecurityException, InstantiationException, InvocationTargetException {
		range(obj);
		if(TypeExtension.isSimple(obj.getClass())) return;
		List<Champ> champs = TypeExtension.getFields(obj.getClass(), this);
		for(Champ champ : champs){
				Class<?> type = champ.type;
				Object objFils = champ.get(obj);
				if (objFils == null){
					//void.class
				}else if(type.isEnum() || champ.isSimple){
					aTraiter(objFils);
				}else if (dictionaryType.isAssignableFrom(type)){
					@SuppressWarnings("unchecked")
					Map<Object, Object> map = (Map<Object, Object>)objFils;
					for(Entry<Object, Object> entry : map.entrySet()){
						Object key = entry.getKey();
						Object value = entry.getValue();
						aTraiter(key);
						aTraiter(value);
					}
				}else if (type != stringType && collectionType.isAssignableFrom(type)){
					Collection<?> collection = (Collection<?>)objFils;
					for(Object iter : collection){
						aTraiter(iter);
					}
				}else if (type.getPackage() == null || ! type.getPackage().getName().startsWith("System")){
					aTraiter(objFils);					
				}else if (dateType.isAssignableFrom(type)){
					aTraiter(objFils);
				}
		}
	}

	private void range(Object obj) throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if(obj.getClass().isPrimitive()){
			
			Constructor<?> constructor = TypeExtension.getTypeEnveloppe(obj.getClass()).getConstructor(obj.getClass()); 
			obj = constructor.newInstance(obj);
		}
		Set<Object> set = getSet(obj);
		set.add(obj);
	}
	
	private void aTraiter(Object obj){
		if(!estDejaTraite(obj)) aTraiter.add(obj);
	}

	private boolean estDejaTraite(Object obj){
		Set<Object> set = getSet(obj);
		return set.contains(obj);
	}
	
	

	private Set<Object> getSet(Object obj) {
		Class<?> clazz = obj.getClass();
		Set<Object> ret = classement.get(clazz);
		if(ret == null){
			ret = new HashSet<>();
			classement.put(clazz, ret);
		}
		return ret;
	}
	
	@Override public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("classe;nombre instances");
		sb.append(System.lineSeparator());
		for (Entry<Class<?>, Set<Object>> entry : classement.entrySet() ){
			Class<?> clazz = entry.getKey();
			int nombre = entry.getValue().size();
			sb.append(clazz.getSimpleName() + ";" + nombre);
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}
	
	public int getNombreInstance(Class<?> clazz){
		return classement.get(clazz) == null ? 0 : classement.get(clazz).size();
	}
	
	private class Champ{
		
		 Field info;
		 Class<?> type;
		 boolean isSimple;

		 Object get(Object obj) throws IllegalArgumentException, IllegalAccessException {
				return info.get(obj);
		}

		public Champ(Field info, Boolean isSimple) {
			this.info = info;
			this.isSimple = isSimple;
			if (info != null) {
				type = info.getType();
			}
		}
		
	}
	
	private static class TypeExtension {
		
		private static Set<Class<?>> simpleTypes = new HashSet<Class<?>>(Arrays.asList(Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, 
				String.class, Date.class, void.class, UUID.class)); 

		public static boolean isSimple(Class<?> type) {
			return type.isPrimitive() || type.isEnum() || simpleTypes.contains(type);
		}

		private static Map<Class<?>, List<Champ>> serializablefieldsOfType = new HashMap<Class<?>, List<Champ>>();
			
		public static List<Champ> getFields(Class<?> type, Statistiques statistiques) {
			List<Champ> fields = serializablefieldsOfType.get(type);
			if (fields == null){
				fields = new ArrayList<Champ>();
				Class<?> parent = type;
				List<Field> fieldstmp = new ArrayList<>();
				while(parent != Object.class){
					fieldstmp.addAll(Arrays.asList(parent.getDeclaredFields()));
					parent = parent.getSuperclass();
				}
				for (Field info : fieldstmp) {
					info.setAccessible(true);
					if (!Modifier.isFinal(info.getModifiers())) {//on ne compte pas les attributs finaux
						Class<?> fieldType = info.getType();
						Champ champ = statistiques.new Champ(info, isSimple(fieldType));
						fields.add(champ);
					}
				}			
				serializablefieldsOfType.put(type, fields) ;
			}
			return fields;
		}
		
		private static Map<Class<?>, Class<?>> dicoTypePrimitifToEnveloppe = new HashMap<>();
		static {
			dicoTypePrimitifToEnveloppe.put(boolean.class, Boolean.class);
			dicoTypePrimitifToEnveloppe.put(char.class, Character.class);
			dicoTypePrimitifToEnveloppe.put(byte.class, Byte.class);
			dicoTypePrimitifToEnveloppe.put(short.class, Short.class);
			dicoTypePrimitifToEnveloppe.put(int.class, Integer.class);
			dicoTypePrimitifToEnveloppe.put(long.class, Long.class);
			dicoTypePrimitifToEnveloppe.put(float.class, Float.class);
			dicoTypePrimitifToEnveloppe.put(double.class, Double.class);
		}
		public static Class<?> getTypeEnveloppe(Class<?> typePrimitif){
			return dicoTypePrimitifToEnveloppe.get(typePrimitif);
		}
	}
	
	private class SetQueue<E> implements Queue<E>{

		private Set<E> queue = new LinkedHashSet<>();
		
		
		@Override
		public int size() {
			return queue.size();
		}

		@Override
		public boolean isEmpty() {
			return queue.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return queue.contains(o);
		}

		@Override
		public Iterator<E> iterator() {
			return queue.iterator();
		}

		@Override
		public Object[] toArray() {
			return queue.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return queue.toArray(a);
		}

		@Override
		public boolean remove(Object o) {
			return queue.remove(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return queue.containsAll(c);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return queue.addAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return queue.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return queue.retainAll(c);
		}

		@Override
		public void clear() {
			queue.clear();
		}

		@Override
		public boolean add(E e) {
			return queue.add(e);
		}

		@Override
		public boolean offer(E e) {
			return queue.add(e);
		}

		@Override
		public E remove() {
			if(queue.isEmpty()) throw new NoSuchElementException();
			E e = queue.iterator().next();
			queue.remove(e);
			return e;
		}

		@Override
		public E poll() {
			if(queue.isEmpty()) return null;
			E e = queue.iterator().next();
			queue.remove(e);
			return e;
		}

		@Override
		public E element() {
			if(queue.isEmpty()) return null;
			E e = queue.iterator().next();
			return e;
		}

		@Override
		public E peek() {
			if(queue.isEmpty()) throw new NoSuchElementException();
			E e = queue.iterator().next();
			return e;
		}
	}
}
