package fr.cloneo.statistiques;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
	
	private Queue<Object> aTraiter = new LinkedList<>();

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
			Object obj = aTraiter.poll();
			if(!estDejaTraite(obj)){
				traite(obj);
			}
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
				fields = new LinkedList<Champ>();
				Class<?> parent = type;
				List<Field> fieldstmp = new LinkedList<>();
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
	
	

}
