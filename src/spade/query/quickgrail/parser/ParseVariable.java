/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.query.quickgrail.parser;

import java.util.ArrayList;

import spade.query.quickgrail.types.Type;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class ParseVariable extends ParseExpression {
  private ParseString name;
  private Type type;

  public ParseVariable(int lineNumber, int columnNumber,
                       ParseString name, Type type) {
    super(lineNumber, columnNumber, ParseExpression.ExpressionType.kVariable);
    this.name = name;
    this.type = type;
  }

  public ParseString getName() {
    return name;
  }

  public Type getType() {
    return type;
  }

  @Override
  public String getLabel() {
    return "Variable";
  }

  @Override
  protected void getFieldStringItems(
      ArrayList<String> inline_field_names,
      ArrayList<String> inline_field_values,
      ArrayList<String> non_container_child_field_names,
      ArrayList<TreeStringSerializable> non_container_child_fields,
      ArrayList<String> container_child_field_names,
      ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields) {
    inline_field_names.add("name");
    inline_field_values.add(name.getValue());
    inline_field_names.add("type");
    inline_field_values.add(type.getName());
  }
}