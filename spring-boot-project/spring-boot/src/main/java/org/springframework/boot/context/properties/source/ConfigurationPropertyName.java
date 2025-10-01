/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A configuration property name composed of elements separated by dots. User created
 * names may contain the characters "{@code a-z}" "{@code 0-9}") and "{@code -}", they
 * must be lower-case and must start with an alphanumeric character. The "{@code -}" is
 * used purely for formatting, i.e. "{@code foo-bar}" and "{@code foobar}" are considered
 * equivalent.
 * <p>
 * The "{@code [}" and "{@code ]}" characters may be used to indicate an associative
 * index(i.e. a {@link Map} key or a {@link Collection} index). Indexes names are not
 * restricted and are considered case-sensitive.
 * <p>
 * Here are some typical examples:
 * <ul>
 * <li>{@code spring.main.banner-mode}</li>
 * <li>{@code server.hosts[0].name}</li>
 * <li>{@code log[org.springboot].level}</li>
 * </ul>
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @see #of(CharSequence)
 * @see ConfigurationPropertySource
 * @since 2.0.0
 */
public final class ConfigurationPropertyName implements Comparable<ConfigurationPropertyName> {

	private static final String EMPTY_STRING = "";

	/**
	 * An empty {@link ConfigurationPropertyName}.
	 */
	public static final ConfigurationPropertyName EMPTY = new ConfigurationPropertyName(Elements.EMPTY);

	private Elements elements;

	private final CharSequence[] uniformElements;

	private String string;

	private int hashCode;

	/**
	 * 这个构造函数是私有的，也就是只允许内部使用
	 *
	 * @param elements Elements
	 */
	private ConfigurationPropertyName(Elements elements) {
		this.elements = elements;
		this.uniformElements = new CharSequence[elements.getSize()];
	}

	/**
	 * Returns {@code true} if this {@link ConfigurationPropertyName} is empty.
	 *
	 * @return {@code true} if the name is empty
	 */
	public boolean isEmpty() {
		return this.elements.getSize() == 0;
	}

	/**
	 * Return if the last element in the name is indexed.
	 *
	 * @return {@code true} if the last element is indexed
	 */
	public boolean isLastElementIndexed() {
		int size = getNumberOfElements();
		return (size > 0 && isIndexed(size - 1));
	}

	/**
	 * Return {@code true} if any element in the name is indexed.
	 *
	 * @return if the element has one or more indexed elements
	 * @since 2.2.10
	 */
	public boolean hasIndexedElement() {
		for (int i = 0; i < getNumberOfElements(); i++) {
			if (isIndexed(i)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return if the element in the name is indexed.
	 *
	 * @param elementIndex the index of the element
	 * @return {@code true} if the element is indexed
	 */
	boolean isIndexed(int elementIndex) {
		return this.elements.getType(elementIndex).isIndexed();
	}

	/**
	 * Return if the element in the name is indexed and numeric.
	 *
	 * @param elementIndex the index of the element
	 * @return {@code true} if the element is indexed and numeric
	 */
	public boolean isNumericIndex(int elementIndex) {
		return this.elements.getType(elementIndex) == ElementType.NUMERICALLY_INDEXED;
	}

	/**
	 * Return the last element in the name in the given form.
	 *
	 * @param form the form to return
	 * @return the last element
	 */
	public String getLastElement(Form form) {
		int size = getNumberOfElements();
		return (size != 0) ? getElement(size - 1, form) : EMPTY_STRING;
	}

	/**
	 * Return an element in the name in the given form.
	 *
	 * @param elementIndex the element index
	 * @param form         the form to return
	 * @return the last element
	 */
	public String getElement(int elementIndex, Form form) {
		CharSequence element = this.elements.get(elementIndex);
		ElementType type = this.elements.getType(elementIndex);
		if (type.isIndexed()) {
			return element.toString();
		}

		// 如果需要获取原始格式
		if (form == Form.ORIGINAL) {
			// 如果非统一格式，就直接返回
			if (type != ElementType.NON_UNIFORM) {
				return element.toString();
			}
			return convertToOriginalForm(element).toString();
		}
		if (form == Form.DASHED) {
			if (type == ElementType.UNIFORM || type == ElementType.DASHED) {
				return element.toString();
			}
			return convertToDashedElement(element).toString();
		}
		CharSequence uniformElement = this.uniformElements[elementIndex];
		if (uniformElement == null) {
			uniformElement = (type != ElementType.UNIFORM) ? convertToUniformElement(element) : element;
			this.uniformElements[elementIndex] = uniformElement.toString();
		}
		return uniformElement.toString();
	}

	private CharSequence convertToOriginalForm(CharSequence element) {
		// 不强制转换为小写
		// 保留 '_' 和合法字符
		return convertElement(element, false,
				(ch, i) -> ch == '_' || ElementsParser.isValidChar(Character.toLowerCase(ch), i));
	}

	private CharSequence convertToDashedElement(CharSequence element) {
		return convertElement(element, true, ElementsParser::isValidChar);
	}

	private CharSequence convertToUniformElement(CharSequence element) {
		return convertElement(element, true, (ch, i) -> ElementsParser.isAlphaNumeric(ch));
	}

	/**
	 * 通用的字符串处理工具
	 * <p>
	 * 遍历 element 每个字符，
	 * - 根据 lowercase 决定强制转换为小写，还是保留原始字符
	 * - 根据 filter 决定是否保留
	 */
	private CharSequence convertElement(CharSequence element, boolean lowercase, ElementCharPredicate filter) {
		StringBuilder result = new StringBuilder(element.length());
		for (int i = 0; i < element.length(); i++) {
			char ch = lowercase ? Character.toLowerCase(element.charAt(i)) : element.charAt(i);
			if (filter.test(ch, i)) {
				result.append(ch);
			}
		}
		return result;
	}

	/**
	 * Return the total number of elements in the name.
	 *
	 * @return the number of elements
	 */
	public int getNumberOfElements() {
		return this.elements.getSize();
	}

	/**
	 * Create a new {@link ConfigurationPropertyName} by appending the given suffix.
	 *
	 * @param suffix the elements to append
	 * @return a new {@link ConfigurationPropertyName}
	 * @throws InvalidConfigurationPropertyNameException if the result is not valid
	 */
	public ConfigurationPropertyName append(String suffix) {
		if (!StringUtils.hasLength(suffix)) {
			return this;
		}
		Elements additionalElements = probablySingleElementOf(suffix);
		return new ConfigurationPropertyName(this.elements.append(additionalElements));
	}

	/**
	 * Create a new {@link ConfigurationPropertyName} by appending the given suffix.
	 *
	 * @param suffix the elements to append
	 * @return a new {@link ConfigurationPropertyName}
	 * @since 2.5.0
	 */
	public ConfigurationPropertyName append(ConfigurationPropertyName suffix) {
		if (suffix == null) {
			return this;
		}
		return new ConfigurationPropertyName(this.elements.append(suffix.elements));
	}

	/**
	 * Return the parent of this {@link ConfigurationPropertyName} or
	 * {@link ConfigurationPropertyName#EMPTY} if there is no parent.
	 *
	 * @return the parent name
	 */
	public ConfigurationPropertyName getParent() {
		int numberOfElements = getNumberOfElements();
		return (numberOfElements <= 1) ? EMPTY : chop(numberOfElements - 1);
	}

	/**
	 * Return a new {@link ConfigurationPropertyName} by chopping this name to the given
	 * {@code size}. For example, {@code chop(1)} on the name {@code foo.bar} will return
	 * {@code foo}.
	 *
	 * @param size the size to chop
	 * @return the chopped name
	 */
	public ConfigurationPropertyName chop(int size) {
		if (size >= getNumberOfElements()) {
			return this;
		}
		return new ConfigurationPropertyName(this.elements.chop(size));
	}

	/**
	 * Return a new {@link ConfigurationPropertyName} by based on this name offset by
	 * specific element index. For example, {@code chop(1)} on the name {@code foo.bar}
	 * will return {@code bar}.
	 *
	 * @param offset the element offset
	 * @return the sub name
	 * @since 2.5.0
	 */
	public ConfigurationPropertyName subName(int offset) {
		if (offset == 0) {
			return this;
		}
		if (offset == getNumberOfElements()) {
			return EMPTY;
		}
		if (offset < 0 || offset > getNumberOfElements()) {
			throw new IndexOutOfBoundsException("Offset: " + offset + ", NumberOfElements: " + getNumberOfElements());
		}
		return new ConfigurationPropertyName(this.elements.subElements(offset));
	}

	/**
	 * Returns {@code true} if this element is an immediate parent of the specified name.
	 *
	 * @param name the name to check
	 * @return {@code true} if this name is an ancestor
	 */
	public boolean isParentOf(ConfigurationPropertyName name) {
		Assert.notNull(name, "Name must not be null");
		if (getNumberOfElements() != name.getNumberOfElements() - 1) {
			return false;
		}
		return isAncestorOf(name);
	}

	/**
	 * Returns {@code true} if this element is an ancestor (immediate or nested parent) of
	 * the specified name.
	 *
	 * @param name the name to check
	 * @return {@code true} if this name is an ancestor
	 */
	public boolean isAncestorOf(ConfigurationPropertyName name) {
		Assert.notNull(name, "Name must not be null");
		if (getNumberOfElements() >= name.getNumberOfElements()) {
			return false;
		}
		return elementsEqual(name);
	}

	@Override
	public int compareTo(ConfigurationPropertyName other) {
		return compare(this, other);
	}

	private int compare(ConfigurationPropertyName n1, ConfigurationPropertyName n2) {
		int l1 = n1.getNumberOfElements();
		int l2 = n2.getNumberOfElements();
		int i1 = 0;
		int i2 = 0;
		while (i1 < l1 || i2 < l2) {
			try {
				ElementType type1 = (i1 < l1) ? n1.elements.getType(i1) : null;
				ElementType type2 = (i2 < l2) ? n2.elements.getType(i2) : null;
				String e1 = (i1 < l1) ? n1.getElement(i1++, Form.UNIFORM) : null;
				String e2 = (i2 < l2) ? n2.getElement(i2++, Form.UNIFORM) : null;
				int result = compare(e1, type1, e2, type2);
				if (result != 0) {
					return result;
				}
			} catch (ArrayIndexOutOfBoundsException ex) {
				throw new RuntimeException(ex);
			}
		}
		return 0;
	}

	private int compare(String e1, ElementType type1, String e2, ElementType type2) {
		if (e1 == null) {
			return -1;
		}
		if (e2 == null) {
			return 1;
		}
		int result = Boolean.compare(type2.isIndexed(), type1.isIndexed());
		if (result != 0) {
			return result;
		}
		if (type1 == ElementType.NUMERICALLY_INDEXED && type2 == ElementType.NUMERICALLY_INDEXED) {
			long v1 = Long.parseLong(e1);
			long v2 = Long.parseLong(e2);
			return Long.compare(v1, v2);
		}
		return e1.compareTo(e2);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != getClass()) {
			return false;
		}
		ConfigurationPropertyName other = (ConfigurationPropertyName) obj;
		if (getNumberOfElements() != other.getNumberOfElements()) {
			return false;
		}
		if (this.elements.canShortcutWithSource(ElementType.UNIFORM)
				&& other.elements.canShortcutWithSource(ElementType.UNIFORM)) {
			return toString().equals(other.toString());
		}
		return elementsEqual(other);
	}

	private boolean elementsEqual(ConfigurationPropertyName name) {
		for (int i = this.elements.getSize() - 1; i >= 0; i--) {
			if (elementDiffers(this.elements, name.elements, i)) {
				return false;
			}
		}
		return true;
	}

	private boolean elementDiffers(Elements e1, Elements e2, int i) {
		ElementType type1 = e1.getType(i);
		ElementType type2 = e2.getType(i);
		if (type1.allowsFastEqualityCheck() && type2.allowsFastEqualityCheck()) {
			return !fastElementEquals(e1, e2, i);
		}
		if (type1.allowsDashIgnoringEqualityCheck() && type2.allowsDashIgnoringEqualityCheck()) {
			return !dashIgnoringElementEquals(e1, e2, i);
		}
		return !defaultElementEquals(e1, e2, i);
	}

	private boolean fastElementEquals(Elements e1, Elements e2, int i) {
		int length1 = e1.getLength(i);
		int length2 = e2.getLength(i);
		if (length1 == length2) {
			int i1 = 0;
			while (length1-- != 0) {
				char ch1 = e1.charAt(i, i1);
				char ch2 = e2.charAt(i, i1);
				if (ch1 != ch2) {
					return false;
				}
				i1++;
			}
			return true;
		}
		return false;
	}

	private boolean dashIgnoringElementEquals(Elements e1, Elements e2, int i) {
		int l1 = e1.getLength(i);
		int l2 = e2.getLength(i);
		int i1 = 0;
		int i2 = 0;
		while (i1 < l1) {
			if (i2 >= l2) {
				return remainderIsDashes(e1, i, i1);
			}
			char ch1 = e1.charAt(i, i1);
			char ch2 = e2.charAt(i, i2);
			if (ch1 == '-') {
				i1++;
			} else if (ch2 == '-') {
				i2++;
			} else if (ch1 != ch2) {
				return false;
			} else {
				i1++;
				i2++;
			}
		}
		if (i2 < l2) {
			if (e2.getType(i).isIndexed()) {
				return false;
			}
			do {
				char ch2 = e2.charAt(i, i2++);
				if (ch2 != '-') {
					return false;
				}
			}
			while (i2 < l2);
		}
		return true;
	}

	private boolean defaultElementEquals(Elements e1, Elements e2, int i) {
		int l1 = e1.getLength(i);
		int l2 = e2.getLength(i);
		boolean indexed1 = e1.getType(i).isIndexed();
		boolean indexed2 = e2.getType(i).isIndexed();
		int i1 = 0;
		int i2 = 0;
		while (i1 < l1) {
			if (i2 >= l2) {
				return remainderIsNotAlphanumeric(e1, i, i1);
			}
			char ch1 = indexed1 ? e1.charAt(i, i1) : Character.toLowerCase(e1.charAt(i, i1));
			char ch2 = indexed2 ? e2.charAt(i, i2) : Character.toLowerCase(e2.charAt(i, i2));
			if (!indexed1 && !ElementsParser.isAlphaNumeric(ch1)) {
				i1++;
			} else if (!indexed2 && !ElementsParser.isAlphaNumeric(ch2)) {
				i2++;
			} else if (ch1 != ch2) {
				return false;
			} else {
				i1++;
				i2++;
			}
		}
		if (i2 < l2) {
			return remainderIsNotAlphanumeric(e2, i, i2);
		}
		return true;
	}

	private boolean remainderIsNotAlphanumeric(Elements elements, int element, int index) {
		if (elements.getType(element).isIndexed()) {
			return false;
		}
		int length = elements.getLength(element);
		do {
			char c = Character.toLowerCase(elements.charAt(element, index++));
			if (ElementsParser.isAlphaNumeric(c)) {
				return false;
			}
		}
		while (index < length);
		return true;
	}

	private boolean remainderIsDashes(Elements elements, int element, int index) {
		if (elements.getType(element).isIndexed()) {
			return false;
		}
		int length = elements.getLength(element);
		do {
			char c = Character.toLowerCase(elements.charAt(element, index++));
			if (c != '-') {
				return false;
			}
		}
		while (index < length);
		return true;
	}

	@Override
	public int hashCode() {
		int hashCode = this.hashCode;
		Elements elements = this.elements;
		if (hashCode == 0 && elements.getSize() != 0) {
			for (int elementIndex = 0; elementIndex < elements.getSize(); elementIndex++) {
				int elementHashCode = 0;
				boolean indexed = elements.getType(elementIndex).isIndexed();
				int length = elements.getLength(elementIndex);
				for (int i = 0; i < length; i++) {
					char ch = elements.charAt(elementIndex, i);
					if (!indexed) {
						ch = Character.toLowerCase(ch);
					}
					if (ElementsParser.isAlphaNumeric(ch)) {
						elementHashCode = 31 * elementHashCode + ch;
					}
				}
				hashCode = 31 * hashCode + elementHashCode;
			}
			this.hashCode = hashCode;
		}
		return hashCode;
	}

	@Override
	public String toString() {
		if (this.string == null) {
			this.string = buildToString();
		}
		return this.string;
	}

	/**
	 * 用于 toString 获取属性的名字
	 *
	 * @return
	 */
	private String buildToString() {
		// 每个元素只能是 UNIFORM 或者 DASHED
		// --> 等价于元素必须只能包含 0-9 a-z 以及 -
		if (this.elements.canShortcutWithSource(ElementType.UNIFORM, ElementType.DASHED)) {
			// 如果满足这样的条件，直接取所有元素组成的 source
			// 这是一个快捷路径
			return this.elements.getSource().toString();
		}

		// 接下来就是一些更复杂的处理





		int elements = getNumberOfElements();
		StringBuilder result = new StringBuilder(elements * 8);
		for (int i = 0; i < elements; i++) {
			boolean indexed = isIndexed(i);
			if (result.length() > 0 && !indexed) {
				result.append('.');
			}
			if (indexed) {
				result.append('[');
				result.append(getElement(i, Form.ORIGINAL));
				result.append(']');
			} else {
				result.append(getElement(i, Form.DASHED));
			}
		}
		return result.toString();
	}

	/**
	 * Returns if the given name is valid. If this method returns {@code true} then the
	 * name may be used with {@link #of(CharSequence)} without throwing an exception.
	 *
	 * @param name the name to test
	 * @return {@code true} if the name is valid
	 */
	public static boolean isValid(CharSequence name) {
		return of(name, true) != null;
	}

	/**
	 * Return a {@link ConfigurationPropertyName} for the specified string.
	 *
	 * @param name the source name
	 * @return a {@link ConfigurationPropertyName} instance
	 * @throws InvalidConfigurationPropertyNameException if the name is not valid
	 */
	public static ConfigurationPropertyName of(CharSequence name) {
		return of(name, false);
	}

	/**
	 * Return a {@link ConfigurationPropertyName} for the specified string or {@code null}
	 * if the name is not valid.
	 *
	 * @param name the source name
	 * @return a {@link ConfigurationPropertyName} instance
	 * @since 2.3.1
	 */
	public static ConfigurationPropertyName ofIfValid(CharSequence name) {
		return of(name, true);
	}

	/**
	 * Return a {@link ConfigurationPropertyName} for the specified string.
	 *
	 * @param name                the source name
	 * @param returnNullIfInvalid if null should be returned if the name is not valid
	 *                            如果 name 无效，是否应该返回 null
	 * @return a {@link ConfigurationPropertyName} instance
	 * @throws InvalidConfigurationPropertyNameException if the name is not valid and
	 *                                                   {@code returnNullIfInvalid} is {@code false}
	 */
	static ConfigurationPropertyName of(CharSequence name, boolean returnNullIfInvalid) {
		// 通过静态方法 elementsOf 得到 Elements 对象
		Elements elements = elementsOf(name, returnNullIfInvalid);
		return (elements != null) ? new ConfigurationPropertyName(elements) : null;
	}

	private static Elements probablySingleElementOf(CharSequence name) {
		return elementsOf(name, false, 1);
	}

	private static Elements elementsOf(CharSequence name, boolean returnNullIfInvalid) {
		return elementsOf(name, returnNullIfInvalid, ElementsParser.DEFAULT_CAPACITY);
	}

	private static Elements elementsOf(CharSequence name, boolean returnNullIfInvalid, int parserCapacity) {
		if (name == null) {
			Assert.isTrue(returnNullIfInvalid, "Name must not be null");
			return null;
		}
		if (name.length() == 0) {
			return Elements.EMPTY;
		}

		// 以 . 开头或者以 . 结尾都是无效
		if (name.charAt(0) == '.' || name.charAt(name.length() - 1) == '.') {
			if (returnNullIfInvalid) {
				return null;
			}
			throw new InvalidConfigurationPropertyNameException(name, Collections.singletonList('.'));
		}

		// 使用 '.' 作为分隔符
		Elements elements = new ElementsParser(name, '.', parserCapacity)
				.parse();
		for (int i = 0; i < elements.getSize(); i++) {
			// 如果 element 不是统一格式
			if (elements.getType(i) == ElementType.NON_UNIFORM) {
				if (returnNullIfInvalid) {
					return null;
				}
				throw new InvalidConfigurationPropertyNameException(name, getInvalidChars(elements, i));
			}
		}
		return elements;
	}

	private static List<Character> getInvalidChars(Elements elements, int index) {
		List<Character> invalidChars = new ArrayList<>();
		for (int charIndex = 0; charIndex < elements.getLength(index); charIndex++) {
			char ch = elements.charAt(index, charIndex);
			if (!ElementsParser.isValidChar(ch, charIndex)) {
				invalidChars.add(ch);
			}
		}
		return invalidChars;
	}

	/**
	 * Create a {@link ConfigurationPropertyName} by adapting the given source. See
	 * {@link #adapt(CharSequence, char, Function)} for details.
	 *
	 * @param name      the name to parse
	 * @param separator the separator used to split the name
	 * @return a {@link ConfigurationPropertyName}
	 */
	public static ConfigurationPropertyName adapt(CharSequence name, char separator) {
		return adapt(name, separator, null);
	}

	/**
	 * Create a {@link ConfigurationPropertyName} by adapting the given source. The name
	 * is split into elements around the given {@code separator}. This method is more
	 * lenient than {@link #of} in that it allows mixed case names and '{@code _}'
	 * characters. Other invalid characters are stripped out during parsing.
	 * <p>
	 * The {@code elementValueProcessor} function may be used if additional processing is
	 * required on the extracted element values.
	 *
	 * @param name                  the name to parse
	 * @param separator             the separator used to split the name
	 * @param elementValueProcessor a function to process element values
	 * @return a {@link ConfigurationPropertyName}
	 */
	static ConfigurationPropertyName adapt(CharSequence name, char separator,
										   Function<CharSequence, CharSequence> elementValueProcessor) {
		Assert.notNull(name, "Name must not be null");
		if (name.length() == 0) {
			return EMPTY;
		}
		Elements elements = new ElementsParser(name, separator).parse(elementValueProcessor);
		if (elements.getSize() == 0) {
			return EMPTY;
		}
		return new ConfigurationPropertyName(elements);
	}

	/**
	 * The various forms that a non-indexed element value can take.
	 */
	public enum Form {

		/**
		 * The original form as specified when the name was created or adapted. For
		 * example:
		 * <ul>
		 * <li>"{@code foo-bar}" = "{@code foo-bar}"</li>
		 * <li>"{@code fooBar}" = "{@code fooBar}"</li>
		 * <li>"{@code foo_bar}" = "{@code foo_bar}"</li>
		 * <li>"{@code [Foo.bar]}" = "{@code Foo.bar}"</li>
		 * </ul>
		 */
		ORIGINAL,

		/**
		 * The dashed configuration form (used for toString; lower-case with only
		 * alphanumeric characters and dashes).
		 * <ul>
		 * <li>"{@code foo-bar}" = "{@code foo-bar}"</li>
		 * <li>"{@code fooBar}" = "{@code foobar}"</li>
		 * <li>"{@code foo_bar}" = "{@code foobar}"</li>
		 * <li>"{@code [Foo.bar]}" = "{@code Foo.bar}"</li>
		 * </ul>
		 */
		DASHED,

		/**
		 * The uniform configuration form (used for equals/hashCode; lower-case with only
		 * alphanumeric characters).
		 * <ul>
		 * <li>"{@code foo-bar}" = "{@code foobar}"</li>
		 * <li>"{@code fooBar}" = "{@code foobar}"</li>
		 * <li>"{@code foo_bar}" = "{@code foobar}"</li>
		 * <li>"{@code [Foo.bar]}" = "{@code Foo.bar}"</li>
		 * </ul>
		 */
		UNIFORM

	}

	/**
	 * Allows access to the individual elements that make up the name. We store the
	 * indexes in arrays rather than a list of object in order to conserve memory.
	 */
	private static class Elements {

		private static final int[] NO_POSITION = {};

		private static final ElementType[] NO_TYPE = {};

		public static final Elements EMPTY = new Elements("", 0, NO_POSITION, NO_POSITION, NO_TYPE, null);

		private final CharSequence source;

		private final int size;

		private final int[] start;

		private final int[] end;

		private final ElementType[] type;

		/**
		 * Contains any resolved elements or can be {@code null} if there aren't any.
		 * Resolved elements allow us to modify the element values in some way (or example
		 * when adapting with a mapping function, or when append has been called). Note
		 * that this array is not used as a cache, in fact, when it's not null then
		 * {@link #canShortcutWithSource} will always return false which may hurt
		 * performance.
		 */
		private final CharSequence[] resolved;

		Elements(CharSequence source, int size, int[] start, int[] end, ElementType[] type, CharSequence[] resolved) {
			super();
			this.source = source;
			this.size = size;
			this.start = start;
			this.end = end;
			this.type = type;
			this.resolved = resolved;
		}

		Elements append(Elements additional) {
			int size = this.size + additional.size;
			ElementType[] type = new ElementType[size];
			System.arraycopy(this.type, 0, type, 0, this.size);
			System.arraycopy(additional.type, 0, type, this.size, additional.size);
			CharSequence[] resolved = newResolved(size);
			for (int i = 0; i < additional.size; i++) {
				resolved[this.size + i] = additional.get(i);
			}
			return new Elements(this.source, size, this.start, this.end, type, resolved);
		}

		Elements chop(int size) {
			CharSequence[] resolved = newResolved(size);
			return new Elements(this.source, size, this.start, this.end, this.type, resolved);
		}

		Elements subElements(int offset) {
			int size = this.size - offset;
			CharSequence[] resolved = newResolved(size);
			int[] start = new int[size];
			System.arraycopy(this.start, offset, start, 0, size);
			int[] end = new int[size];
			System.arraycopy(this.end, offset, end, 0, size);
			ElementType[] type = new ElementType[size];
			System.arraycopy(this.type, offset, type, 0, size);
			return new Elements(this.source, size, start, end, type, resolved);
		}

		private CharSequence[] newResolved(int size) {
			CharSequence[] resolved = new CharSequence[size];
			if (this.resolved != null) {
				System.arraycopy(this.resolved, 0, resolved, 0, Math.min(size, this.size));
			}
			return resolved;
		}

		int getSize() {
			return this.size;
		}

		CharSequence get(int index) {
			if (this.resolved != null && this.resolved[index] != null) {
				return this.resolved[index];
			}
			int start = this.start[index];
			int end = this.end[index];
			return this.source.subSequence(start, end);
		}

		int getLength(int index) {
			if (this.resolved != null && this.resolved[index] != null) {
				return this.resolved[index].length();
			}
			int start = this.start[index];
			int end = this.end[index];
			return end - start;
		}

		char charAt(int index, int charIndex) {
			if (this.resolved != null && this.resolved[index] != null) {
				return this.resolved[index].charAt(charIndex);
			}
			int start = this.start[index];
			return this.source.charAt(start + charIndex);
		}

		ElementType getType(int index) {
			return this.type[index];
		}

		CharSequence getSource() {
			return this.source;
		}

		/**
		 * Returns if the element source can be used as a shortcut for an operation such
		 * as {@code equals} or {@code toString}.
		 *
		 * @param requiredType the required type
		 * @return {@code true} if all elements match at least one of the types
		 */
		boolean canShortcutWithSource(ElementType requiredType) {
			return canShortcutWithSource(requiredType, requiredType);
		}

		/**
		 * Returns if the element source can be used as a shortcut for an operation such
		 * as {@code equals} or {@code toString}.
		 *
		 * @param requiredType    the required type
		 * @param alternativeType and alternative required type
		 * @return {@code true} if all elements match at least one of the types
		 */
		boolean canShortcutWithSource(ElementType requiredType, ElementType alternativeType) {
			if (this.resolved != null) {
				return false;
			}
			for (int i = 0; i < this.size; i++) {
				ElementType type = this.type[i];

				// 每个元素的 type 必须是 requiredType 或 alternativeType
				if (type != requiredType && type != alternativeType) {
					return false;
				}

				// this.end[i - 1] + 1 == this.start[i] 这意味着两个元素之间只间隔 1 个字符
				// 例如 foo.bar -> foo 与 bar (间隔 .)
				//     hosts[0] -> hosts 与 0 间隔 [
				if (i > 0 && this.end[i - 1] + 1 != this.start[i]) {
					return false;
				}
			}
			return true;
		}

	}

	/**
	 * Main parsing logic used to convert a {@link CharSequence} to {@link Elements}.
	 * <p>
	 * [静态类]负责将字符串解析为一个 Elements，每个元素都有其 ElementType
	 */
	private static class ElementsParser {

		private static final int DEFAULT_CAPACITY = 6;

		private final CharSequence source;

		/**
		 * 以 separator 作为分隔符，拆分元素
		 */
		private final char separator;

		private int size;

		private int[] start;

		private int[] end;

		private ElementType[] type;

		private CharSequence[] resolved;

		ElementsParser(CharSequence source, char separator) {
			this(source, separator, DEFAULT_CAPACITY);
		}

		ElementsParser(CharSequence source, char separator, int capacity) {
			this.source = source;
			this.separator = separator;
			this.start = new int[capacity];
			this.end = new int[capacity];
			this.type = new ElementType[capacity];
		}

		Elements parse() {
			return parse(null);
		}

		Elements parse(Function<CharSequence, CharSequence> valueProcessor) {
			int length = this.source.length();
			int openBracketCount = 0;
			int start = 0;

			// 在关键分隔符的节点会变更 type
			ElementType type = ElementType.EMPTY;
			for (int i = 0; i < length; i++) {
				// 获取当前字符，这个很好理解
				char ch = this.source.charAt(i);

				// 下面判断当前字符是否是特殊分隔符

				// 1. [
				if (ch == '[') {
					// 如果 openBracketCount == 0，那么所有括号都闭合了，开启新的解析
					if (openBracketCount == 0) {
						add(start, i, type, valueProcessor); // 添加
						start = i + 1; // 标记开始位置
						type = ElementType.NUMERICALLY_INDEXED; // 假定接下来的类型是 "数字"
					}
					// 如果 openBracketCount != 0，那么不对称，可能是 [A[B]C]
					openBracketCount++;
				}
				// 2. ]
				else if (ch == ']') {
					openBracketCount--;
					// 如果 openBracketCount != 0，那么不对称，可能是 [A[B]C]，只是退出了 [B] 这个 key 的组成部分而已
					if (openBracketCount == 0) {
						add(start, i, type, valueProcessor);
						start = i + 1;
						type = ElementType.EMPTY;
					}
				}
				// 3. 分隔符
				else if (!type.isIndexed() && ch == this.separator) {
					add(start, i, type, valueProcessor);
					start = i + 1;
					type = ElementType.EMPTY;
				}
				// 4. 其他字符
				else {
					// 更新 type
					// 根据现在的情况，重新校准 type
					type = updateType(type, ch, i - start);
				}
			}
			if (openBracketCount != 0) {
				type = ElementType.NON_UNIFORM;
			}
			add(start, length, type, valueProcessor);
			return new Elements(this.source, this.size, this.start, this.end, this.type, this.resolved);
		}

		private ElementType updateType(ElementType existingType, char ch, int index) {
			// 当前正在索引中 [可能是数字索引][可能是非数字索引]
			if (existingType.isIndexed()) {
				// 如果被误认为是数字索引，但是又不是数字，那么变更为非数字索引
				if (existingType == ElementType.NUMERICALLY_INDEXED && !isNumeric(ch)) {
					return ElementType.INDEXED;
				}
				return existingType;
			}

			// 如果当前是 EMPTY，并且遇到合法字符，那么进入有效字符阶段
			if (existingType == ElementType.EMPTY && isValidChar(ch, index)) {
				// 如果当前是 element 的开头 (index == 0)，那么一切很好，元素是 统一的
				// 如果当前 index > 0，说明已经跳过了一些字符，却依然是 EMPTY，那么这是一个不规范的名字
				return (index == 0) ? ElementType.UNIFORM : ElementType.NON_UNIFORM;
			}
			if (existingType == ElementType.UNIFORM && ch == '-') {
				return ElementType.DASHED;
			}

			// 当前 index 出现的字符不是合法字符
			if (!isValidChar(ch, index)) {
				// 思考：
				// 1. 如果遇到一个非法字符，那么一般认为理解可以返回 NON_UNIFORM
				// 2. 但是，这里有一个特殊情况，有可能还没遇到开头的字符，所以需要检查是否保持 EMPTY 的状态

				if (existingType == ElementType.EMPTY && !isValidChar(Character.toLowerCase(ch), index)) {
					return ElementType.EMPTY;
				}
				return ElementType.NON_UNIFORM;
			}
			return existingType;
		}

		private void add(int start, int end, ElementType type, Function<CharSequence, CharSequence> valueProcessor) {
			// start end 之间甚至都没有元素空间
			if ((end - start) < 1 || type == ElementType.EMPTY) {
				return;
			}

			// 扩容
			if (this.start.length == this.size) {
				this.start = expand(this.start);
				this.end = expand(this.end);
				this.type = expand(this.type);
				this.resolved = expand(this.resolved);
			}

			if (valueProcessor != null) {
				if (this.resolved == null) {
					this.resolved = new CharSequence[this.start.length];
				}
				// 转换成一个新的字符串
				CharSequence resolved = valueProcessor.apply(this.source.subSequence(start, end));
				Elements resolvedElements = new ElementsParser(resolved, '.').parse();
				Assert.state(resolvedElements.getSize() == 1, "Resolved element must not contain multiple elements");
				this.resolved[this.size] = resolvedElements.get(0);
				type = resolvedElements.getType(0);
			}

			// 保存这个位置的元素 start
			this.start[this.size] = start;
			// 保存这个位置的元素 end
			this.end[this.size] = end;
			// 保存这个位置的元素类型 type
			this.type[this.size] = type;
			// 追加一个新的元素，所以 size++，只有这个地方 size 会增加
			this.size++;
		}

		private int[] expand(int[] src) {
			int[] dest = new int[src.length + DEFAULT_CAPACITY];
			System.arraycopy(src, 0, dest, 0, src.length);
			return dest;
		}

		private ElementType[] expand(ElementType[] src) {
			ElementType[] dest = new ElementType[src.length + DEFAULT_CAPACITY];
			System.arraycopy(src, 0, dest, 0, src.length);
			return dest;
		}

		private CharSequence[] expand(CharSequence[] src) {
			if (src == null) {
				return null;
			}
			CharSequence[] dest = new CharSequence[src.length + DEFAULT_CAPACITY];
			System.arraycopy(src, 0, dest, 0, src.length);
			return dest;
		}

		/**
		 * 合法的字符是小写字母、数字、短横线（不在开头）
		 */
		static boolean isValidChar(char ch, int index) {
			return isAlpha(ch) || isNumeric(ch) || (index != 0 && ch == '-');
		}

		static boolean isAlphaNumeric(char ch) {
			return isAlpha(ch) || isNumeric(ch);
		}

		private static boolean isAlpha(char ch) {
			return ch >= 'a' && ch <= 'z';
		}

		private static boolean isNumeric(char ch) {
			return ch >= '0' && ch <= '9';
		}

	}

	/**
	 * The various types of element that we can detect.
	 */
	private enum ElementType {

		/**
		 * The element is logically empty (contains no valid chars).
		 */
		EMPTY(false),

		/**
		 * The element is a uniform name (a-z, 0-9, no dashes, lowercase).
		 */
		UNIFORM(false),

		/**
		 * The element is almost uniform, but it contains (but does not start with) at
		 * least one dash.
		 */
		DASHED(false),

		/**
		 * The element contains non-uniform characters and will need to be converted.
		 * <p>
		 * 不统一的格式，含有大小写混合、下划线、空格等异常字符
		 */
		NON_UNIFORM(false),

		/**
		 * The element is non-numerically indexed.
		 */
		INDEXED(true),

		/**
		 * The element is numerically indexed.
		 */
		NUMERICALLY_INDEXED(true);

		private final boolean indexed;

		ElementType(boolean indexed) {
			this.indexed = indexed;
		}

		public boolean isIndexed() {
			return this.indexed;
		}

		public boolean allowsFastEqualityCheck() {
			return this == UNIFORM || this == NUMERICALLY_INDEXED;
		}

		public boolean allowsDashIgnoringEqualityCheck() {
			return allowsFastEqualityCheck() || this == DASHED;
		}

	}

	/**
	 * Predicate used to filter element chars.
	 */
	private interface ElementCharPredicate {

		boolean test(char ch, int index);

	}

}
